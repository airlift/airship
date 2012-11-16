/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.airship.coordinator;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.simpledb.AmazonSimpleDB;
import com.amazonaws.services.simpledb.AmazonSimpleDBClient;
import com.google.common.base.Preconditions;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import io.airlift.configuration.ConfigurationModule;
import io.airlift.airship.coordinator.auth.AuthorizedKeyStore;
import io.airlift.airship.coordinator.auth.S3AuthorizedKeyStore;

import javax.inject.Singleton;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class AwsProvisionerModule
        implements Module
{
    public void configure(Binder binder)
    {
        binder.disableCircularProxies();
        binder.requireExplicitBindings();

        binder.bind(Provisioner.class).to(AwsProvisioner.class).in(Scopes.SINGLETON);
        binder.bind(StateManager.class).to(SimpleDbStateManager.class).in(Scopes.SINGLETON);
        binder.bind(AuthorizedKeyStore.class).to(S3AuthorizedKeyStore.class).in(Scopes.SINGLETON);
        ConfigurationModule.bindConfig(binder).to(AwsProvisionerConfig.class);
    }

    @Provides
    @Singleton
    public AmazonS3 providesS3(AWSCredentials awsCredentials)
    {
        return new AmazonS3Client(awsCredentials);
    }

    @Provides
    @Singleton
    public AmazonSimpleDB provideSimpleDb(AWSCredentials awsCredentials)
    {
        return new AmazonSimpleDBClient(awsCredentials);
    }

    @Provides
    @Singleton
    public AmazonEC2 provideAmazonEC2(AwsProvisionerConfig provisionerConfig, AWSCredentials awsCredentials)
    {
        AmazonEC2Client client = new AmazonEC2Client(awsCredentials);

        //Use the config to override the default endpoint in the ec2 client.
        if (provisionerConfig.getAwsEndpoint() != null) {
            client.setEndpoint(provisionerConfig.getAwsEndpoint());
        }

        return client;
    }

    @Provides
    @Singleton
    public AWSCredentials provideAwsCredentials(AwsProvisionerConfig provisionerConfig)
            throws IOException
    {
        File credentialsFile = new File(provisionerConfig.getAwsCredentialsFile());
        Properties properties = new Properties();
        FileInputStream in = new FileInputStream(credentialsFile);
        try {
            properties.load(in);
        }
        finally {
            in.close();
        }

        String accessKey = properties.getProperty("aws.access-key");
        Preconditions.checkArgument(accessKey != null, "aws credentials file does not contain a value for aws.access-key");
        String secretKey = properties.getProperty("aws.secret-key");
        Preconditions.checkArgument(secretKey != null, "aws credentials file does not contain a value for aws.secret-key");

        return new BasicAWSCredentials(accessKey, secretKey);
    }
}
