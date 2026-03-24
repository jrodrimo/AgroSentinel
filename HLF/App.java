/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.TlsChannelCredentials;
import org.hyperledger.fabric.client.CommitException;
import org.hyperledger.fabric.client.CommitStatusException;
import org.hyperledger.fabric.client.Contract;
import org.hyperledger.fabric.client.EndorseException;
import org.hyperledger.fabric.client.Gateway;
import org.hyperledger.fabric.client.GatewayException;
import org.hyperledger.fabric.client.Hash;
import org.hyperledger.fabric.client.SubmitException;
import org.hyperledger.fabric.client.identity.Identities;
import org.hyperledger.fabric.client.identity.Identity;
import org.hyperledger.fabric.client.identity.Signer;
import org.hyperledger.fabric.client.identity.Signers;
import org.hyperledger.fabric.client.identity.X509Identity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.cert.CertificateException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

public final class App {
    private static final String MSP_ID = System.getenv().getOrDefault("MSP_ID", "Org1MSP");
    private static final String CHANNEL_NAME = System.getenv().getOrDefault("CHANNEL_NAME", "mychannel");
    private static final String CHAINCODE_NAME = System.getenv().getOrDefault("CHAINCODE_NAME", "basic");

    // Path to crypto materials.
    private static final Path CRYPTO_PATH = Paths.get("../../test-network/organizations/peerOrganizations/org1.example.com");
    // Path to user certificate.
    private static final Path CERT_DIR_PATH = CRYPTO_PATH.resolve(Paths.get("users/User1@org1.example.com/msp/signcerts"));
    // Path to user private key directory.
    private static final Path KEY_DIR_PATH = CRYPTO_PATH.resolve(Paths.get("users/User1@org1.example.com/msp/keystore"));
    // Path to peer tls certificate.
    private static final Path TLS_CERT_PATH = CRYPTO_PATH.resolve(Paths.get("peers/peer0.org1.example.com/tls/ca.crt"));

    // Gateway peer end point.
    private static final String PEER_ENDPOINT = "localhost:7051";
    private static final String OVERRIDE_AUTH = "peer0.org1.example.com";

    private final Contract contract;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // Registro de prueba para marzo de 2026
    private final String assetId = "estadoActual" + Instant.now().toEpochMilli();
    private final String cid = "bafybeigdummycid123456789abcdefghijklmnop";
    private final String sha256 = "4f9c2b3a1d8e7f6a5b4c3d2e1f00112233445566778899aabbccddeeff001122";
    private final String timestamp = Instant.now().toString();
    private final String source = "drone";

    public static void main(final String[] args) throws Exception {
        var channel = newGrpcConnection();

        var builder = Gateway.newInstance()
                .identity(newIdentity())
                .signer(newSigner())
                .hash(Hash.SHA256)
                .connection(channel)
                .evaluateOptions(options -> options.withDeadlineAfter(5, TimeUnit.SECONDS))
                .endorseOptions(options -> options.withDeadlineAfter(15, TimeUnit.SECONDS))
                .submitOptions(options -> options.withDeadlineAfter(5, TimeUnit.SECONDS))
                .commitStatusOptions(options -> options.withDeadlineAfter(1, TimeUnit.MINUTES));

        try (var gateway = builder.connect()) {
            new App(gateway).run();
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
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

    private static Path getFirstFilePath(final Path dirPath) throws IOException {
        try (var keyFiles = Files.list(dirPath)) {
            return keyFiles.findFirst().orElseThrow();
        }
    }

    public App(final Gateway gateway) {
        var network = gateway.getNetwork(CHANNEL_NAME);
        contract = network.getContract(CHAINCODE_NAME);
    }

    public void run() throws GatewayException, CommitException {
        getAllAssets();
        createAsset();
        readAssetById();
        getAllAssets();
        updateAsset();
        readAssetById();
        updateNonExistentAsset();
    }

    /**
     * En este contrato InitLedger no crea datos iniciales, pero lo mantenemos
     * porque existe en el chaincode y no molesta.
     */
    private void initLedger() throws EndorseException, SubmitException, CommitStatusException, CommitException {
        System.out.println("\n--> Submit Transaction: InitLedger");
        contract.submitTransaction("InitLedger");
        System.out.println("*** Transaction committed successfully");
    }

    /**
     * Consulta todos los registros actuales del ledger.
     */
    private void getAllAssets() throws GatewayException {
        System.out.println("\n--> Evaluate Transaction: GetAllAssets, returns all current IPFS/Fabric records");

        var result = contract.evaluateTransaction("GetAllAssets");

        System.out.println("*** Result: " + prettyJson(result));
    }

    private String prettyJson(final byte[] json) {
        return prettyJson(new String(json, StandardCharsets.UTF_8));
    }

    private String prettyJson(final String json) {
        var parsedJson = JsonParser.parseString(json);
        return gson.toJson(parsedJson);
    }

    /**
     * Crea un nuevo registro con id, cid, sha256, timestamp y source.
     */
    private void createAsset() throws EndorseException, SubmitException, CommitStatusException, CommitException {
        System.out.println("\n--> Submit Transaction: CreateAsset, creates a new IPFS/Fabric traceability record");

        contract.submitTransaction(
                "CreateAsset",
                assetId,
                cid,
                sha256,
                timestamp,
                source
        );

        System.out.println("*** Transaction committed successfully");
        System.out.println("*** Created asset with ID: " + assetId);
    }

    /**
     * Lee un registro concreto por ID.
     */
    private void readAssetById() throws GatewayException {
        System.out.println("\n--> Evaluate Transaction: ReadAsset, returns the stored record");

        var evaluateResult = contract.evaluateTransaction("ReadAsset", assetId);

        System.out.println("*** Result: " + prettyJson(evaluateResult));
    }

    /**
     * Actualiza el mismo registro con un CID y source distintos, simulando una nueva evidencia.
     */
    private void updateAsset() throws EndorseException, SubmitException, CommitStatusException, CommitException {
        System.out.println("\n--> Submit Transaction: UpdateAsset, updates an existing IPFS/Fabric traceability record");

        String updatedCid = cid + "-v2";
        String updatedSha256 = "9a8b7c6d5e4f00112233445566778899aabbccddeeff00112233445566778899";
        String updatedTimestamp = Instant.now().toString();
        String updatedSource = "uav";

        contract.submitTransaction(
                "UpdateAsset",
                assetId,
                updatedCid,
                updatedSha256,
                updatedTimestamp,
                updatedSource
        );

        System.out.println("*** Transaction committed successfully");
    }

    /**
     * Provoca a propósito un error sobre un asset inexistente.
     */
    private void updateNonExistentAsset() {
        try {
            System.out.println("\n--> Submit Transaction: UpdateAsset on non-existent asset");

            contract.submitTransaction(
                    "UpdateAsset",
                    "estadoActual_inexistente",
                    "bafybeifakecid0000000000000000000000000",
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    Instant.now().toString(),
                    "drone"
            );

            System.out.println("******** FAILED to return an error");
        } catch (EndorseException | SubmitException | CommitStatusException e) {
            System.out.println("*** Successfully caught the error:");
            e.printStackTrace(System.out);
            System.out.println("Transaction ID: " + e.getTransactionId());
        } catch (CommitException e) {
            System.out.println("*** Successfully caught the error:");
            e.printStackTrace(System.out);
            System.out.println("Transaction ID: " + e.getTransactionId());
            System.out.println("Status code: " + e.getCode());
        }
    }
}
