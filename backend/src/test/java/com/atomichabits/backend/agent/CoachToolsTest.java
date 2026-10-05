package com.atomichabits.backend.agent;

import com.atomichabits.backend.repository.UserRepository;
import com.atomichabits.backend.service.HabitService;
import com.atomichabits.backend.service.MemoryService;
import com.atomichabits.backend.service.MoodService;
import com.atomichabits.backend.service.UserService;
import io.agentscope.core.agent.Agent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CoachToolsTest {

    private final UserService userService = mock(UserService.class);
    private final AgentUserRegistry registry = new AgentUserRegistry();
    private final Agent agent = mock(Agent.class);
    private CoachTools tools;

    @BeforeEach
    void setUp() {
        tools = new CoachTools(userService, mock(HabitService.class), mock(MoodService.class),
                mock(MemoryService.class), mock(UserRepository.class),
                new TrackingThreadSessionManager(10, 5), registry);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void usesAgentBoundUserWhenSecurityContextIsEmpty() {
        registry.bind(agent, "owner@example.com");

        tools.saveUserIdentity("", "I am a reader", agent);

        verify(userService).updateIdentity("owner@example.com", "I am a reader");
    }

    @Test
    void ignoresModelSuppliedEmailInFavourOfAuthenticatedUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("owner@example.com", null, List.of()));

        tools.saveUserIdentity("victim@example.com", "I am hacked", agent);

        verify(userService).updateIdentity("owner@example.com", "I am hacked");
        verify(userService, never()).updateIdentity("victim@example.com", "I am hacked");
    }

    @Test
    void refusesToActWhenNoServerSideIdentityExists() {
        String result = tools.saveUserIdentity("victim@example.com", "I am hacked", agent);

        assertThat(result).contains("not authenticated");
        verify(userService, never()).updateIdentity(anyString(), anyString());
    }
}
