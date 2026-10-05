package com.atomichabits.backend.agent;

import com.atomichabits.backend.model.ChatMessage;
import com.atomichabits.backend.model.User;
import com.atomichabits.backend.repository.ChatMessageRepository;
import com.atomichabits.backend.repository.UserRepository;
import com.atomichabits.backend.service.CoachTurnMemoryHitService;
import com.atomichabits.backend.service.MemoryService;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoachLongTermMemoryTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
    private final User user = User.builder().id(7L).email("owner@example.com").build();
    private CoachLongTermMemory memory;

    @BeforeEach
    void setUp() {
        memory = new CoachLongTermMemory(mock(MemoryService.class), mock(CoachTurnMemoryHitService.class),
                userRepository, chatMessageRepository);
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(user));
    }

    @Test
    void persistsOnlyTheLatestUserTurnAndFinalReply() {
        when(chatMessageRepository.findByUserIdOrderByTimestampDesc(eq(7L), any(Pageable.class))).thenReturn(List.of());

        memory.persistLatestTurn("owner@example.com", List.of(
                msg(MsgRole.USER, "old question"),
                msg(MsgRole.ASSISTANT, "old answer"),
                msg(MsgRole.USER, "new question"),
                msg(MsgRole.ASSISTANT, ""),            // tool-call step without text
                msg(MsgRole.ASSISTANT, "new answer")));

        ArgumentCaptor<ChatMessage> saved = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(ChatMessage::getRole, ChatMessage::getContent)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("user", "new question"),
                        org.assertj.core.groups.Tuple.tuple("ai", "new answer"));
    }

    @Test
    void doesNotSaveTheSameTurnTwice() {
        when(chatMessageRepository.findByUserIdOrderByTimestampDesc(eq(7L), any(Pageable.class))).thenReturn(List.of(
                ChatMessage.builder().role("ai").content("answer").build(),
                ChatMessage.builder().role("user").content("question").build()));

        memory.persistLatestTurn("owner@example.com",
                List.of(msg(MsgRole.USER, "question"), msg(MsgRole.ASSISTANT, "answer")));

        verify(chatMessageRepository, never()).save(any());
    }

    private static Msg msg(MsgRole role, String text) {
        return Msg.builder().role(role).content(TextBlock.builder().text(text).build()).build();
    }
}
