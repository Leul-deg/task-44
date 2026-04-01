package com.shiftworks.jobops.service;

import com.shiftworks.jobops.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PasswordPolicyService {

    private final AppProperties appProperties;

    public List<String> validate(String password) {
        List<String> violations = new ArrayList<>();
        int minLength = appProperties.getSecurity().getPassword().getMinLength();
        if (password == null || password.length() < minLength) {
            violations.add("Password must be at least " + minLength + " characters long");
        }
        if (password != null) {
            if (!password.chars().anyMatch(Character::isUpperCase)) {
                violations.add("Password must include an uppercase letter");
            }
            if (!password.chars().anyMatch(Character::isLowerCase)) {
                violations.add("Password must include a lowercase letter");
            }
            if (!password.chars().anyMatch(Character::isDigit)) {
                violations.add("Password must include a digit");
            }
            if (password.chars().noneMatch(ch -> !Character.isLetterOrDigit(ch))) {
                violations.add("Password must include a special character");
            }
        }
        return violations;
    }

    public boolean isValid(String password) {
        return validate(password).isEmpty();
    }
}
