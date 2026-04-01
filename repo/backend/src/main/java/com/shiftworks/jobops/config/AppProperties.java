package com.shiftworks.jobops.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Security security = new Security();
    private Storage storage = new Storage();
    private Session session = new Session();
    private RateLimit rateLimit = new RateLimit();
    private JobValidation jobValidation = new JobValidation();

    @Data
    public static class Security {
        private String aesSecretKey;
        private Password password = new Password();
        private int captchaAfterFailures;
        private int lockAfterFailures;
        private int lockDurationMinutes;
    }

    @Data
    public static class Password {
        private int minLength;
        private int rotationDays;
    }

    @Data
    public static class Storage {
        private String filePath;
        private String backupPath;
    }

    @Data
    public static class Session {
        private int idleTimeoutMinutes;
        private int absoluteTimeoutHours;
    }

    @Data
    public static class RateLimit {
        private int perMinute = 60;
        private int authPerMinute = 20;
    }

    @Data
    public static class JobValidation {
        private ValidityDays validityDays = new ValidityDays();
        private Range hourlyPay = new Range();
        private Range flatPay = new Range();
        private Range headcount = new Range();
        private Range weeklyHours = new Range();
    }

    @Data
    public static class Range {
        private int min;
        private int max;
    }

    @Data
    public static class ValidityDays {
        private int defaultDays;
        private int max;
    }
}
