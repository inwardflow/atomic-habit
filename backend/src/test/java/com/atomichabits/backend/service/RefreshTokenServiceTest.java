package com.atomichabits.backend.service;

import com.atomichabits.backend.dto.DeviceMetadata;
import com.atomichabits.backend.model.RefreshToken;
import com.atomichabits.backend.model.User;
import com.atomichabits.backend.repository.RefreshTokenRepository;
import com.atomichabits.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenServiceTest {

    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final DeviceService deviceService = mock(DeviceService.class);
    private final RefreshTokenService service = new RefreshTokenService();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "refreshTokenRepository", repository);
        ReflectionTestUtils.setField(service, "userRepository", userRepository);
        ReflectionTestUtils.setField(service, "deviceService", deviceService);
        ReflectionTestUtils.setField(service, "refreshTokenDurationMs", 60_000L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));
        when(deviceService.parseUserAgent(any())).thenReturn(DeviceMetadata.builder().build());
        when(repository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void storesOnlyTheHashOfAHighEntropyToken() {
        RefreshTokenService.IssuedToken issued = service.createRefreshToken(1L, "127.0.0.1", "UA", "device");

        assertThat(issued.value()).hasSizeGreaterThanOrEqualTo(43); // 256 bits, base64url
        assertThat(issued.entity().getToken())
                .isNotEqualTo(issued.value())
                .isEqualTo(RefreshTokenService.hash(issued.value()))
                .hasSize(64);
    }

    @Test
    void looksUpAndDeletesByHashOfThePresentedToken() {
        service.findByToken("raw-cookie-value");
        service.deleteByToken("raw-cookie-value");

        String hash = RefreshTokenService.hash("raw-cookie-value");
        verify(repository).findByToken(hash);
        verify(repository).deleteByToken(hash);
    }

    @Test
    void blankTokenNeverHitsTheDatabase() {
        assertThat(service.findByToken(" ")).isEmpty();
        service.deleteByToken(null);

        verify(repository, org.mockito.Mockito.never()).findByToken(anyString());
        verify(repository, org.mockito.Mockito.never()).deleteByToken(anyString());
    }
}
