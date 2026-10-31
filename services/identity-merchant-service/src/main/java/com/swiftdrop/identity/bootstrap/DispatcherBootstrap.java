package com.swiftdrop.identity.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.swiftdrop.identity.common.ApiException;
import com.swiftdrop.identity.config.IdentityProperties;
import com.swiftdrop.identity.user.UserService;

/**
 * Nobody can register as a dispatcher, so the first one is created at startup from
 * DISPATCHER_BOOTSTRAP_EMAIL / DISPATCHER_BOOTSTRAP_PASSWORD, but only while no dispatcher
 * exists. That account must change its password at first login. After that, dispatchers create
 * other dispatchers through POST /api/v1/dispatchers, and these variables can be removed.
 */
@Component
public class DispatcherBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DispatcherBootstrap.class);

    private final UserService userService;
    private final IdentityProperties.Bootstrap settings;

    public DispatcherBootstrap(UserService userService, IdentityProperties properties) {
        this.userService = userService;
        this.settings = properties.bootstrap();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userService.anyDispatcherExists()) {
            return;
        }
        String email = settings.dispatcherEmail();
        String password = settings.dispatcherPassword();
        if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
            log.warn("No dispatcher account exists. Set DISPATCHER_BOOTSTRAP_EMAIL and "
                    + "DISPATCHER_BOOTSTRAP_PASSWORD to create the first one at startup.");
            return;
        }
        if (userService.emailExists(email)) {
            log.warn("Cannot create the first dispatcher: DISPATCHER_BOOTSTRAP_EMAIL is already used by "
                    + "another account. Choose a different email.");
            return;
        }
        try {
            userService.createDispatcher(email, settings.dispatcherName(), null, password);
            log.info("Created the first dispatcher account. It must change its password at first login.");
        }
        catch (ApiException ex) {
            // Typically a password that breaks the password rules. Don't stop the service for it.
            log.error("Cannot create the first dispatcher: {} {}", ex.getMessage(), ex.errors());
        }
    }
}
