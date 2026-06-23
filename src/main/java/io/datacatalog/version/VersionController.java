package io.datacatalog.version;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/v1/datasets/{datasetId}/versions")
@SecurityRequirement(name = "bearer-jwt")
public class VersionController {

    private final VersionService service;

    public VersionController(VersionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RequestUploadResponse requestUpload(@PathVariable UUID datasetId) {
        return service.requestUpload(datasetId);
    }

    @GetMapping
    public List<VersionResponse> list(@PathVariable UUID datasetId) {
        return service.listVersions(datasetId);
    }

    @PostMapping("/{versionId}/complete")
    public VersionResponse complete(@PathVariable UUID datasetId,
                                    @PathVariable UUID versionId,
                                    @RequestBody(required = false) CompleteUploadRequest request) {
        return service.complete(datasetId, versionId, request);
    }

    @GetMapping("/{versionId}/download")
    public DownloadResponse download(@PathVariable UUID datasetId, @PathVariable UUID versionId) {
        return service.download(datasetId, versionId);
    }
}
