package com.swiftdrop.identity.bootstrap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


import org.junit.jupiter.api.Test;

import com.swiftdrop.identity.config.IdentityProperties;
import com.swiftdrop.identity.user.UserService;

class DispatcherBootstrapTests {

    private final UserService userService = mock(UserService.class);

    @Test
    void createsTheFirstDispatcherWhenNoneExists() {
        when(userService.anyDispatcherExists()).thenReturn(false);

        bootstrap("ops@swiftdrop.test", "Initial-pass-1").run(null);

        verify(userService).createDispatcher("ops@swiftdrop.test", "SwiftDrop Dispatcher", null, "Initial-pass-1");
    }

    @Test
    void doesNothingOnceADispatcherExists() {
        when(userService.anyDispatcherExists()).thenReturn(true);

        bootstrap("ops@swiftdrop.test", "Initial-pass-1").run(null);

        verify(userService, never()).createDispatcher(any(), any(), any(), any());
    }

    @Test
    void doesNothingWithoutConfiguration() {
        when(userService.anyDispatcherExists()).thenReturn(false);

        bootstrap("", "").run(null);

        verify(userService, never()).createDispatcher(any(), any(), any(), any());
    }

    @Test
    void doesNotTakeOverAnExistingAccount() {
        when(userService.anyDispatcherExists()).thenReturn(false);
        when(userService.emailExists("customer@example.com")).thenReturn(true);

        bootstrap("customer@example.com", "Initial-pass-1").run(null);

        verify(userService, never()).createDispatcher(any(), any(), any(), any());
    }

    private DispatcherBootstrap bootstrap(String email, String password) {
        IdentityProperties properties = new IdentityProperties(null, null,
                new IdentityProperties.Bootstrap(email, password, "SwiftDrop Dispatcher"), null);
        return new DispatcherBootstrap(userService, properties);
    }
}
