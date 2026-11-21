package br.com.ateliedigital.storage.config;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "atelie.storage")
public record StorageProperties(
         @NotBlank
         String endpoint,

         @NotBlank
         String publicUrl,

         @NotBlank
         String accessKey,

         @NotBlank
         String secretKey,

         @NotBlank
         String bucket,

         @NotNull
         DataSize maxFileSize

) {
}
