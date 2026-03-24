package org.hyperledger.fabric.samples.assettransfer;

import com.owlike.genson.annotation.JsonProperty;

public final class Asset {
    private final String id;
    private final String cid;
    private final String sha256;
    private final String timestamp;
    private final String source;

    public Asset(@JsonProperty("id") final String id,
                 @JsonProperty("cid") final String cid,
                 @JsonProperty("sha256") final String sha256,
                 @JsonProperty("timestamp") final String timestamp,
                 @JsonProperty("source") final String source) {
        this.id = id;
        this.cid = cid;
        this.sha256 = sha256;
        this.timestamp = timestamp;
        this.source = source;
    }

    public String getId() {
        return id;
    }

    public String getCid() {
        return cid;
    }

    public String getSha256() {
        return sha256;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getSource() {
        return source;
    }

    @Override
    public String toString() {
        return "Asset{"
                + "id='" + id + '\''
                + ", cid='" + cid + '\''
                + ", sha256='" + sha256 + '\''
                + ", timestamp='" + timestamp + '\''
                + ", source='" + source + '\''
                + '}';
    }
}
