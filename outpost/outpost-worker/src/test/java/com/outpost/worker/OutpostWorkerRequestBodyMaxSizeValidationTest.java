package com.outpost.worker;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.framework.security.web.SizeBoundedRequestBody;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.tomcat.autoconfigure.TomcatServerProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.util.unit.DataSize;

class OutpostWorkerRequestBodyMaxSizeValidationTest {

  @Test
  void limitsContainerRequestBodiesToTheMaxSize() throws IOException {
    TomcatServerProperties tomcat =
        new Binder(
                new MapConfigurationPropertySource(
                    PropertiesLoaderUtils.loadProperties(
                        new FileSystemResource("src/main/resources/application.properties"))))
            .bindOrCreate("server.tomcat", TomcatServerProperties.class);

    DataSize maxSize = DataSize.ofBytes(SizeBoundedRequestBody.MAX_SIZE_BYTES);
    assertThat(tomcat.getMaxSwallowSize()).isEqualTo(maxSize);
    assertThat(tomcat.getMaxHttpFormPostSize()).isEqualTo(maxSize);
  }
}
