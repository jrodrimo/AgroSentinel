package com.cirrusminor.fabricbridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.TlsChannelCredentials;
import org.hyperledger.fabric.client.Contract;
import org.hyperledger.fabric.client.Gateway;
import org.hyperledger.fabric.client.Hash;
import org.hyperledger.fabric.client.identity.Identities;
import org.hyperledger.fabric.client.identity.Identity;
import org.hyperledger.fabric.client.identity.Signer;
import org.hyperledger.fabric.client.identity.Signers;
import org.hyperledger.fabric.client.identity.X509Identity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.cert.CertificateException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static spark.Spark.*;

public class FabricBridgeServer {

    private static final String MSP_ID = System.getenv().getOrDefault("MSP_ID", "Org1MSP");
    private static final String CHANNEL_NAME = System.getenv().getOrDefault("CHANNEL_NAME", "mychannel");
    private static final String CHAINCODE_NAME = System.getenv().getOrDefault("CHAINCODE_NAME", "basic");

    private static final Path CRYPTO_PATH =
            Paths.get("/home/hlf/fabric-samples/test-network/organizations/peerOrganizations/org1.example.com");
    private static final Path CERT_DIR_PATH =
            CRYPTO_PATH.resolve(Paths.get("users/User1@org1.example.com/msp/signcerts"));
    private static final Path KEY_DIR_PATH =
            CRYPTO_PATH.resolve(Paths.get("users/User1@org1.example.com/msp/keystore"));
    private static final Path TLS_CERT_PATH =
            CRYPTO_PATH.resolve(Paths.get("peers/peer0.org1.example.com/tls/ca.crt"));

    private static final String PEER_ENDPOINT = "localhost:7051";
    private static final String OVERRIDE_AUTH = "peer0.org1.example.com";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) {
        port(8088);

        post("/fabric/register", (req, res) -> {
            res.type("application/json");

            try {
                RegisterRequest payload = GSON.fromJson(req.body(), RegisterRequest.class);

                validate(payload);

                if (payload.timestamp == null || payload.timestamp.isBlank()) {
                    payload.timestamp = Instant.now().toString();
                }

                ManagedChannel channel = newGrpcConnection();
                try {
                    Gateway.Builder gatewayBuilder = Gateway.newInstance()
                            .identity(newIdentity())
                            .signer(newSigner())
                            .hash(Hash.SHA256)
                            .connection(channel)
                            .evaluateOptions(options -> options.withDeadlineAfter(5, TimeUnit.SECONDS))
                            .endorseOptions(options -> options.withDeadlineAfter(15, TimeUnit.SECONDS))
                            .submitOptions(options -> options.withDeadlineAfter(5, TimeUnit.SECONDS))
                            .commitStatusOptions(options -> options.withDeadlineAfter(1, TimeUnit.MINUTES));

                    try (Gateway gateway = gatewayBuilder.connect()) {
                        Contract contract = gateway.getNetwork(CHANNEL_NAME).getContract(CHAINCODE_NAME);

                        contract.submitTransaction(
                                "CreateAsset",
                                payload.id,
                                payload.cid,
                                payload.sha256,
                                payload.timestamp,
                                payload.source
                        );

                        String resultJson = new String(
                                contract.evaluateTransaction("ReadAsset", payload.id)
                        );

                        res.status(200);
                        return GSON.toJson(Map.of(
                                "ok", true,
                                "message", "Asset registered in Fabric",
                                "asset", GSON.fromJson(resultJson, Object.class)
                        ));
                    }
                } finally {
                    channel.shutdownNow();
                    try {
                        channel.awaitTermination(5, TimeUnit.SECONDS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            } catch (IllegalArgumentException e) {
                res.status(400);
                return GSON.toJson(Map.of(
                        "ok", false,
                        "error", e.getMessage()
                ));
            } catch (Exception e) {
                e.printStackTrace();
                res.status(500);
                return GSON.toJson(Map.of(
                        "ok", false,
                        "error", e.getMessage()
                ));
            }
        });

        get("/health", (req, res) -> {
            res.type("application/json");
            return GSON.toJson(Map.of(
                    "ok", true,
                    "service", "cassini-fabric-bridge",
                    "timestamp", Instant.now().toString()
            ));
        });

        System.out.println("Fabric Bridge REST escuchando en http://0.0.0.0:8088");
    }

    private static void validate(RegisterRequest payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Body JSON vacío o inválido");
        }
        if (isBlank(payload.id)) {
            throw new IllegalArgumentException("Campo 'id' obligatorio");
        }
        if (isBlank(payload.cid)) {
            throw new IllegalArgumentException("Campo 'cid' obligatorio");
        }
        if (isBlank(payload.sha256)) {
            throw new IllegalArgumentException("Campo 'sha256' obligatorio");
        }
        if (isBlank(payload.source)) {
            throw new IllegalArgumentException("Campo 'source' obligatorio");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static ManagedChannel newGrpcConnection() throws IOException {
        var credentials = TlsChannelCredentials.newBuilder()
                .trustManager(TLS_CERT_PATH.toFile())
                .build();

        return Grpc.newChannelBuilder(PEER_ENDPOINT, credentials)
                .overrideAuthority(OVERRIDE_AUTH)
                .build();
    }

    private static Identity newIdentity() throws IOException, CertificateException {
        try (var certReader = Files.newBufferedReader(getFirstFilePath(CERT_DIR_PATH))) {
            var certificate = Identities.readX509Certificate(certReader);
            return new X509Identity(MSP_ID, certificate);
        }
    }

    private static Signer newSigner() throws IOException, InvalidKeyException {
        try (var keyReader = Files.newBufferedReader(getFirstFilePath(KEY_DIR_PATH))) {
            var privateKey = Identities.readPrivateKey(keyReader);
            return Signers.newPrivateKeySigner(privateKey);
        }
    }

    private static Path getFirstFilePath(Path dirPath) throws IOException {
        try (var files = Files.list(dirPath)) {
            return files.findFirst().orElseThrow();
        }
    }

    private static class RegisterRequest {
        String id;
        String cid;
        String sha256;
        String timestamp;
        String source;
    }
}
