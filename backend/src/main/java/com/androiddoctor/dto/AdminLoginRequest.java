package com.androiddoctor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminLoginRequest(
        @NotBlank(message = "管理员密码不能为空")
        @Size(max = 500, message = "管理员密码过长")
        String password
) {}

