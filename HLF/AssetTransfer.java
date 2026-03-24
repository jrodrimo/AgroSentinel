package org.hyperledger.fabric.samples.assettransfer;

import java.util.ArrayList;
import java.util.List;

import com.owlike.genson.Genson;
import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.Contact;
import org.hyperledger.fabric.contract.annotation.Contract;
import org.hyperledger.fabric.contract.annotation.Default;
import org.hyperledger.fabric.contract.annotation.Info;
import org.hyperledger.fabric.contract.annotation.License;
import org.hyperledger.fabric.contract.annotation.Transaction;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;

@Contract(
        name = "basic",
        info = @Info(
                title = "Image IPFS Traceability Contract",
                description = "Stores CID + SHA256 + metadata in Hyperledger Fabric",
                version = "1.0.0",
                license = @License(
                        name = "Apache-2.0",
                        url = "http://www.apache.org/licenses/LICENSE-2.0.html"),
                contact = @Contact(
                        email = "admin@example.com",
                        name = "CASSINI Team",
                        url = "https://example.com")))
@Default
public final class AssetTransfer implements ContractInterface {

    private final Genson genson = new Genson();

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public void InitLedger(final Context ctx) {
        // Vacío a propósito
    }

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public void CreateAsset(final Context ctx,
                            final String id,
                            final String cid,
                            final String sha256,
                            final String timestamp,
                            final String source) {

        ChaincodeStub stub = ctx.getStub();

        if (AssetExists(ctx, id)) {
            String errorMessage = String.format("The asset %s already exists", id);
            throw new RuntimeException(errorMessage);
        }

        Asset asset = new Asset(id, cid, sha256, timestamp, source);
        String assetJSON = genson.serialize(asset);
        stub.putStringState(id, assetJSON);
    }

    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String ReadAsset(final Context ctx, final String id) {
    ChaincodeStub stub = ctx.getStub();
    String assetJSON = stub.getStringState(id);

    if (assetJSON == null || assetJSON.isEmpty()) {
        String errorMessage = String.format("The asset %s does not exist", id);
        throw new RuntimeException(errorMessage);
    }

    return assetJSON;
}

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public void UpdateAsset(final Context ctx,
                            final String id,
                            final String cid,
                            final String sha256,
                            final String timestamp,
                            final String source) {

        if (!AssetExists(ctx, id)) {
            String errorMessage = String.format("The asset %s does not exist", id);
            throw new RuntimeException(errorMessage);
        }

        ChaincodeStub stub = ctx.getStub();
        Asset newAsset = new Asset(id, cid, sha256, timestamp, source);
        String assetJSON = genson.serialize(newAsset);
        stub.putStringState(id, assetJSON);
    }

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public void DeleteAsset(final Context ctx, final String id) {
        if (!AssetExists(ctx, id)) {
            String errorMessage = String.format("The asset %s does not exist", id);
            throw new RuntimeException(errorMessage);
        }

        ChaincodeStub stub = ctx.getStub();
        stub.delState(id);
    }

    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public boolean AssetExists(final Context ctx, final String id) {
        ChaincodeStub stub = ctx.getStub();
        String assetJSON = stub.getStringState(id);
        return assetJSON != null && !assetJSON.isEmpty();
    }

    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String GetAllAssets(final Context ctx) {
        ChaincodeStub stub = ctx.getStub();
        List<Asset> results = new ArrayList<>();

        QueryResultsIterator<KeyValue> queryResults = stub.getStateByRange("", "");

        for (KeyValue result : queryResults) {
            Asset asset = genson.deserialize(result.getStringValue(), Asset.class);
            results.add(asset);
        }

        return genson.serialize(results);
    }
}
