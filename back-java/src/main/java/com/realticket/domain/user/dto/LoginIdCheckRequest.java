package com.realticket.domain.user.dto;

import jakarta.validation.constraints.NotNull;

public record LoginIdCheckRequest(@NotNull String loginId) {
}
