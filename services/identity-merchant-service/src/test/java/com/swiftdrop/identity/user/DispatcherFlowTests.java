package com.swiftdrop.identity.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.swiftdrop.identity.ApiTestSupport;

class DispatcherFlowTests extends ApiTestSupport {

    @Test
    void newDispatcherHasNoPowersUntilThePasswordIsChanged() throws Exception {
        String email = uniqueEmail();
        userService.createDispatcher(email, "First Dispatcher", null, INITIAL_PASSWORD);

        Session session = session(login(email, INITIAL_PASSWORD).andExpect(status().isOk())
            .andExpect(jsonPath("$.user.mustChangePassword").value(true))
            .andExpect(jsonPath("$.user.roles").value(contains("DISPATCHER")))
            .andReturn());
        assertThat((List<String>) claims(session.accessToken()).read("$.roles")).isEmpty();
        getWithToken("/api/v1/users", session.accessToken())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        String cookie = mockMvc.perform(post("/api/v1/users/me/password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\": \"%s\", \"newPassword\": \"%s\"}".formatted(INITIAL_PASSWORD, PASSWORD)))
            .andExpect(status().isNoContent())
            .andReturn().getResponse().getCookie(REFRESH_COOKIE).getValue();

        Session active = session(refresh(cookie).andExpect(status().isOk())
            .andExpect(jsonPath("$.user.mustChangePassword").value(false))
            .andReturn());
        assertThat((List<String>) claims(active.accessToken()).read("$.roles")).containsExactly("DISPATCHER");
        getWithToken("/api/v1/users?role=DISPATCHER&q=" + email, active.accessToken())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].email").value(email));
    }

    @Test
    void dispatcherCanListUsersAndCreateDispatchers() throws Exception {
        String dispatcher = activeDispatcherToken();
        String customerEmail = uniqueEmail();
        register(customerEmail, PASSWORD).andExpect(status().isCreated());

        getWithToken("/api/v1/users?role=CUSTOMER&status=ACTIVE&q=" + customerEmail.toUpperCase(), dispatcher)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].roles").value(contains("CUSTOMER")))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20));

        String newEmail = uniqueEmail();
        createDispatcher(dispatcher, newEmail)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value(newEmail))
            .andExpect(jsonPath("$.roles").value(contains("DISPATCHER")))
            .andExpect(jsonPath("$.mustChangePassword").value(true));
        login(newEmail, INITIAL_PASSWORD).andExpect(status().isOk());

        createDispatcher(dispatcher, newEmail).andExpect(status().isConflict());
    }

    @Test
    void customersCannotUseDispatcherEndpoints() throws Exception {
        String customer = registerAndLogin().accessToken();

        getWithToken("/api/v1/users", customer).andExpect(status().isForbidden());
        createDispatcher(customer, uniqueEmail()).andExpect(status().isForbidden());
    }

    @Test
    void listRejectsUnknownSortFields() throws Exception {
        getWithToken("/api/v1/users?sort=passwordHash,asc", activeDispatcherToken())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("sort"));
    }

    private ResultActions createDispatcher(String accessToken, String email) throws Exception {
        return mockMvc.perform(post("/api/v1/dispatchers")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email": "%s", "fullName": "Second Dispatcher", "initialPassword": "%s"}
                """.formatted(email, INITIAL_PASSWORD)));
    }
}
