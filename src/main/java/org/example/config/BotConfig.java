package org.example.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@Data
@PropertySource("application.properties")
public class BotConfig {
    @Value("DeliveryMaslBot")
    String botName;
    @Value("6393581134:AAGc6Gmmk_myLcDEyZFMfR6A8Ck5fXW9IIc")
    String token;
}
