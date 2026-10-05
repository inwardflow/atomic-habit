package com.atomichabits.backend.service;

import com.atomichabits.backend.config.AiModelProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatModelFactoryTest {

    @Test
    void buildsModelFromProperties() {
        AiModelProperties properties = new AiModelProperties();
        properties.getModel().setApiKey("sk-test");
        properties.getModel().setModelName("qwen-test");
        properties.getModel().setBaseUrl("https://example.invalid/v1");

        assertThat(new ChatModelFactory(properties).create(false).getModelName()).isEqualTo("qwen-test");
    }

    @Test
    void proxyIsOnlyUsableWithHostAndPort() {
        AiModelProperties.Proxy proxy = new AiModelProperties.Proxy();
        proxy.setEnabled(true);
        assertThat(proxy.isUsable()).isFalse();

        proxy.setHost("127.0.0.1");
        proxy.setPort(7890);
        assertThat(proxy.isUsable()).isTrue();

        proxy.setEnabled(false);
        assertThat(proxy.isUsable()).isFalse();
    }
}
