package be.sandervl.invoicepdf.config;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.monitoring.ProfileCsmConfigurationProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.costexplorer.AWSCostExplorer;
import com.amazonaws.services.costexplorer.AWSCostExplorerClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class AwsConfiguration {

    public static final String AWS_USER_ACCOUNT = "pdf-invoice";

    @Bean
    @Profile("prod")
    public AWSCostExplorer awsCostExplorer() {
        return AWSCostExplorerClientBuilder.defaultClient();
    }

    @Bean
    @Profile("dev")
    public AWSCostExplorer awsCostExplorerDev() {
        return AWSCostExplorerClientBuilder.standard()
                .withCredentials(new ProfileCredentialsProvider(AWS_USER_ACCOUNT))
                .withRegion(Regions.EU_WEST_1)
                .withClientSideMonitoringConfigurationProvider(new ProfileCsmConfigurationProvider(AWS_USER_ACCOUNT))
                .build();
    }
}
