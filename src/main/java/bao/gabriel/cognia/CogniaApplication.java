package bao.gabriel.cognia;

import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * The auth/user APIs must boot on a plain PostgreSQL instance. The pgvector
 * store is excluded until the knowledge-base features are wired; remove
 * {@link PgVectorStoreAutoConfiguration} from the exclusion list at that point.
 */
@SpringBootApplication(exclude = {
        UserDetailsServiceAutoConfiguration.class,
        PgVectorStoreAutoConfiguration.class
})
@ConfigurationPropertiesScan
public class CogniaApplication {

    public static void main(String[] args) {
        SpringApplication.run(CogniaApplication.class, args);
    }

}
