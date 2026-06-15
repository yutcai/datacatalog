package io.datacatalog.dataset;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import io.datacatalog.user.User;
import io.datacatalog.user.UserRepository;

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
                request.name(), owner.getId(), request.team(), request.description(),
                request.tags(), request.metadata());

        // saveAndFlush so the DB-generated timestamps are read back before mapping.
        Dataset saved = datasets.saveAndFlush(dataset);
        return toResponse(saved, owner.getUsername());
    }

    @Transactional(readOnly = true)
    public DatasetResponse get(UUID id) {
        Dataset dataset = datasets.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "dataset not found"));
        String ownerUsername = users.findById(dataset.getOwnerId())
                .map(User::getUsername)
                .orElse(null);
        return toResponse(dataset, ownerUsername);
    }

    private DatasetResponse toResponse(Dataset d, String ownerUsername) {
        return new DatasetResponse(
                d.getId(), d.getName(), ownerUsername, d.getTeam(), d.getDescription(),
                d.getTags(), d.getMetadata(), d.getCreatedAt(), d.getUpdatedAt());
    }
}
