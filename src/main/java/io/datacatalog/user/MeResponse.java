package io.datacatalog.user;

import java.util.UUID;

public record MeResponse(UUID id, String username) {
}
