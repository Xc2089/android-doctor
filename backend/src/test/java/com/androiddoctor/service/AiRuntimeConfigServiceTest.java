package com.androiddoctor.service;

import com.androiddoctor.config.AppProperties;
import com.androiddoctor.dto.AiConfigUpdateRequest;
import com.androiddoctor.model.AiConfiguration;
import com.androiddoctor.repository.AiConfigurationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiRuntimeConfigServiceTest {
    @TempDir Path tempDir;

    @Test
    void encryptsPersistsAndReloadsConfigurationWithoutExposingKey() {
        AppProperties properties = properties();
        AiConfigurationRepository repository = mock(AiConfigurationRepository.class);
        when(repository.findById(1L)).thenReturn(Optional.empty());
        SecretCryptoService crypto = new SecretCryptoService(new LocalMasterKeyService(properties));
        var service = new AiRuntimeConfigService(properties, repository, crypto);
        service.loadPersistedConfiguration();

        var view = service.update(new AiConfigUpdateRequest(
                true, "https://models.example.com/v1/", "example-model", 30, "secret-key", false));

        ArgumentCaptor<AiConfiguration> captor = ArgumentCaptor.forClass(AiConfiguration.class);
        verify(repository).save(captor.capture());
        AiConfiguration stored = captor.getValue();
        assertThat(stored.getEncryptedApiKey()).doesNotContain("secret-key");
        assertThat(crypto.decrypt(stored.getEncryptedApiKey())).isEqualTo("secret-key");
        assertThat(view.baseUrl()).isEqualTo("https://models.example.com/v1");
        assertThat(view.apiKeyConfigured()).isTrue();
        assertThat(view.source()).isEqualTo("database");
        assertThat(view.toString()).doesNotContain("secret-key");

        AiConfigurationRepository reloadRepository = mock(AiConfigurationRepository.class);
        when(reloadRepository.findById(1L)).thenReturn(Optional.of(stored));
        var reloaded = new AiRuntimeConfigService(properties, reloadRepository, crypto);
        reloaded.loadPersistedConfiguration();
        assertThat(reloaded.settings().apiKey()).isEqualTo("secret-key");
        assertThat(reloaded.view().source()).isEqualTo("database");
    }

    @Test
    void rejectsNonHttpEndpoint() {
        AppProperties properties = properties();
        var repository = mock(AiConfigurationRepository.class);
        var crypto = new SecretCryptoService(new LocalMasterKeyService(properties));
        var service = new AiRuntimeConfigService(properties, repository, crypto);
        assertThatThrownBy(() -> service.update(new AiConfigUpdateRequest(
                true, "file:///tmp/model", "example-model", 30, "", false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTP/HTTPS");
    }

    private AppProperties properties() {
        return new AppProperties(
                "http://localhost:9090", 1_000_000, 100, 30,
                new AppProperties.Ai(false, "http://localhost:11434/v1", "", "local-model", 45,
                        24_000, tempDir.resolve("master.key").toString()),
                new AppProperties.Admin("test-password", tempDir.resolve("admin-password").toString(), 8));
    }
}

