package com.shiftworks.jobops.dto;

import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.enums.UserStatus;

public record AdminUserPageRequest(UserRole role,
                                   UserStatus status,
                                   String search,
                                   int page,
                                   int size) {
}
