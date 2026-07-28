package com.farmflate.dto.user;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserProfileResponseDto {
    private final String email;
    private final String displayName;
    private final String provider;
    private final String role;
}
