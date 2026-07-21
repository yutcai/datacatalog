package io.datacatalog.dataset;

import io.datacatalog.user.User;
import io.datacatalog.user.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DatasetService {

    private final DatasetRepository datasets;
    private final UserRepository users;

    public DatasetService(DatasetRepository datasets, UserRepository users) {
        this.datasets = datasets;
        this.users = users;
    }

    @Transactional
    public DatasetResponse create(String ownerUsername, CreateDatasetRequest request) {
        // Owner is the authenticated user, resolved from the token — never from the request body.
        User owner = users.findByUsername(ownerUsername)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unknown subject"));

        Dataset dataset = new Dataset(
                request.name(),
                owner.getId(),
                request.team(),
                request.description(),
                request.tags(),
                request.metadata());

        // saveAndFlush so the DB-generated timestamps are read back before mapping.
        Dataset saved = datasets.saveAndFlush(dataset);
        return toResponse(saved, owner.getUsername());
    }

    @Transactional(readOnly = true)
    public DatasetResponse get(UUID id) {
        Dataset dataset = datasets.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "dataset not found"));
        String ownerUsername =
                users.findById(dataset.getOwnerId()).map(User::getUsername).orElse(null);
        return toResponse(dataset, ownerUsername);
    }

    private static final int MAX_LIMIT = 100;

    @Transactional(readOnly = true)
    public DatasetPage search(String q, String tag, String owner, int page, int limit) {
        int safePage = Math.max(page, 0);
        int safeLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);

        String ownerId = null;
        if (owner != null && !owner.isBlank()) {
            User ownerUser = users.findByUsername(owner).orElse(null);
            if (ownerUser == null) {
                // An owner filter that matches no user yields an empty page, not an error.
                return new DatasetPage(List.of(), safePage, safeLimit, 0);
            }
            ownerId = ownerUser.getId().toString();
        }

        Page<Dataset> result =
                datasets.search(blankToNull(q), blankToNull(tag), ownerId, PageRequest.of(safePage, safeLimit));

        // Resolve owner usernames in one batch to avoid an N+1 lookup per row.
        Set<UUID> ownerIds =
                result.getContent().stream().map(Dataset::getOwnerId).collect(Collectors.toSet());
        Map<UUID, String> usernames =
                users.findAllById(ownerIds).stream().collect(Collectors.toMap(User::getId, User::getUsername));

        List<DatasetResponse> items = result.getContent().stream()
                .map(d -> toResponse(d, usernames.get(d.getOwnerId())))
                .toList();
        return new DatasetPage(items, safePage, safeLimit, result.getTotalElements());
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    @Transactional
    public DatasetResponse patch(UUID id, String actingUsername, PatchDatasetRequest request) {
        Dataset dataset = datasets.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "dataset not found"));
        User actor = users.findByUsername(actingUsername)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unknown subject"));
        // Per-resource authorization: only the owner may modify; reads stay open for discovery.
        if (!dataset.getOwnerId().equals(actor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "only the owner can modify this dataset");
        }

        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name must not be blank");
            }
            dataset.setName(request.name());
        }
        if (request.team() != null) {
            dataset.setTeam(request.team());
        }
        if (request.description() != null) {
            dataset.setDescription(request.description());
        }
        if (request.tags() != null) {
            dataset.setTags(request.tags());
        }
        if (request.metadata() != null) {
            dataset.mergeMetadata(request.metadata());
        }

        Dataset saved = datasets.saveAndFlush(dataset);
        return toResponse(saved, actor.getUsername());
    }

    private DatasetResponse toResponse(Dataset d, String ownerUsername) {
        return new DatasetResponse(
                d.getId(),
                d.getName(),
                ownerUsername,
                d.getTeam(),
                d.getDescription(),
                d.getTags(),
                d.getMetadata(),
                d.getCreatedAt(),
                d.getUpdatedAt());
    }
}
