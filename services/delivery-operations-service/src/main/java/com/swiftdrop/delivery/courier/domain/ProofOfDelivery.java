package com.swiftdrop.delivery.courier.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "proof_of_delivery")
public class ProofOfDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false, unique = true)
    private DeliveryAssignment assignment;

    @Column(name = "parcel_reference", nullable = false, length = 50)
    private String parcelReference;

    @Column(name = "recipient_name", nullable = false, length = 120)
    private String recipientName;

    @Column(length = 500)
    private String notes;

    @Column(name = "media_url", length = 500)
    private String mediaUrl;

    @Column(name = "delivered_at", nullable = false)
    private Instant deliveredAt;

    /** Cash collected on Cash on Delivery parcels; null otherwise. */
    @Column(name = "collected_amount", precision = 12, scale = 2)
    private BigDecimal collectedAmount;

    /** Reference to the uploaded file; the server-generated key, never returned by the API. Null without a file. */
    @Column(name = "file_key", length = 64)
    private String fileKey;

    @Column(name = "file_name", length = 120)
    private String fileName;

    @Column(name = "file_content_type", length = 50)
    private String fileContentType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    protected ProofOfDelivery() {
    }

    public ProofOfDelivery(DeliveryAssignment assignment, String recipientName, String notes, String mediaUrl,
                           Instant deliveredAt, BigDecimal collectedAmount) {
        this.collectedAmount = collectedAmount;
        this.assignment = assignment;
        this.parcelReference = assignment.getParcelReference();
        this.recipientName = recipientName;
        this.notes = notes;
        this.mediaUrl = mediaUrl;
        this.deliveredAt = deliveredAt;
    }

    public void attachFile(String key, String name, String contentType, long sizeBytes) {
        this.fileKey = key;
        this.fileName = name;
        this.fileContentType = contentType;
        this.fileSizeBytes = sizeBytes;
    }

    public boolean hasFile() { return fileKey != null; }
    public String getFileKey() { return fileKey; }
    public String getFileName() { return fileName; }
    public String getFileContentType() { return fileContentType; }
    public Long getFileSizeBytes() { return fileSizeBytes; }

    public UUID getId() { return id; }
    public DeliveryAssignment getAssignment() { return assignment; }
    public String getParcelReference() { return parcelReference; }
    public String getRecipientName() { return recipientName; }
    public String getNotes() { return notes; }
    public String getMediaUrl() { return mediaUrl; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public BigDecimal getCollectedAmount() { return collectedAmount; }
}
