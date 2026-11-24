package com.swiftdrop.parcel_pricing_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swiftdrop.parcel_pricing_service.client.IdentityClient;
import com.swiftdrop.parcel_pricing_service.client.IdentityClient.IdentityUser;
import com.swiftdrop.parcel_pricing_service.dto.UpdateProfileRequest;
import com.swiftdrop.parcel_pricing_service.entity.CustomerProfile;
import com.swiftdrop.parcel_pricing_service.repository.CustomerProfileRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.oauth2.jwt.Jwt;

/** Identity stays the owner of name/email/phone; the local copy is only a fallback. */
class CustomerProfileServiceTest {
    private final CustomerProfileRepository profiles = mock(CustomerProfileRepository.class);
    private final IdentityClient identity = mock(IdentityClient.class);
    private final CustomerProfileService service = new CustomerProfileService(profiles, identity);
    private final UUID customerId = UUID.randomUUID();
    private final Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject(customerId.toString()).build();
    private final IdentityUser account = new IdentityUser(customerId, "nimal@example.com", "Nimal Perera", "+94771234567");

    @BeforeEach
    void setUp() {
        when(profiles.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private CustomerProfile stored(String name, String email, String phone) {
        CustomerProfile profile = new CustomerProfile(customerId, name, email);
        profile.setPhoneNumber(phone);
        profile.setCity("Colombo");
        when(profiles.findById(customerId)).thenReturn(Optional.of(profile));
        return profile;
    }

    private static UpdateProfileRequest update(String name, String phone, String avatar) {
        return new UpdateProfileRequest(name, "ignored@example.com", phone, " 12 Main St ", " ", null, avatar);
    }

    // ---------- get ----------

    @Test
    void identityOutageFallsBackToTheStoredCopy() {
        stored("Old Name", "old@example.com", "+94770000000");
        when(identity.currentUser(jwt)).thenReturn(Optional.empty());
        var profile = service.getProfile(customerId, jwt);
        assertThat(profile.fullName()).isEqualTo("Old Name");
        assertThat(profile.city()).isEqualTo("Colombo");
    }

    @Test
    void identityOutageWithoutAStoredCopyReturnsAnEmptyProfile() {
        when(profiles.findById(customerId)).thenReturn(Optional.empty());
        when(identity.currentUser(jwt)).thenReturn(Optional.empty());
        var profile = service.getProfile(customerId, jwt);
        assertThat(profile.customerId()).isEqualTo(customerId);
        assertThat(profile.fullName()).isNull();
        assertThat(profile.email()).isNull();
    }

    @Test
    void identityValuesWinAndRefreshAStaleCopy() {
        CustomerProfile copy = stored("Old Name", "old@example.com", null);
        when(identity.currentUser(jwt)).thenReturn(Optional.of(account));
        var profile = service.getProfile(customerId, jwt);
        assertThat(profile.fullName()).isEqualTo("Nimal Perera");
        assertThat(profile.city()).isEqualTo("Colombo");
        assertThat(copy.getFullName()).isEqualTo("Nimal Perera");
        assertThat(copy.getEmail()).isEqualTo("nimal@example.com");
        assertThat(copy.getPhoneNumber()).isEqualTo("+94771234567");
    }

    @Test
    void upToDateCopyIsLeftAloneAndNoCopyIsCreatedOnRead() {
        CustomerProfile copy = stored("Nimal Perera", "nimal@example.com", "+94771234567");
        when(identity.currentUser(jwt)).thenReturn(Optional.of(account));
        service.getProfile(customerId, jwt);
        assertThat(copy.getFullName()).isEqualTo("Nimal Perera");

        when(profiles.findById(customerId)).thenReturn(Optional.empty());
        assertThat(service.getProfile(customerId, jwt).streetAddress()).isNull();
        verify(profiles, never()).save(any());
    }

    // ---------- update ----------

    @Test
    void unchangedNameAndPhoneAreNotSentToIdentity() {
        stored("Nimal Perera", "nimal@example.com", "+94771234567");
        when(identity.currentUser(jwt)).thenReturn(Optional.of(account));
        var profile = service.updateProfile(customerId, update(" Nimal Perera ", "077 123 4567", null), jwt);
        verify(identity, never()).updateCurrentUser(any(), any(), any());
        assertThat(profile.streetAddress()).isEqualTo("12 Main St");
        assertThat(profile.city()).as("blank becomes null").isNull();
    }

    @Test
    void onlyTheChangedFieldIsSentToIdentity() {
        stored("Nimal Perera", "nimal@example.com", "+94771234567");
        when(identity.currentUser(jwt)).thenReturn(Optional.of(account));
        when(identity.updateCurrentUser(jwt, "Nimal K Perera", null))
                .thenReturn(new IdentityUser(customerId, "nimal@example.com", "Nimal K Perera", "+94771234567"));
        var profile = service.updateProfile(customerId, update("Nimal K Perera", "+94771234567", null), jwt);
        assertThat(profile.fullName()).isEqualTo("Nimal K Perera");
        verify(identity).updateCurrentUser(eq(jwt), eq("Nimal K Perera"), isNull());
    }

    @Test
    void phoneChangeIsNormalisedToE164BeforeItIsSent() {
        stored("Nimal Perera", "nimal@example.com", "+94771234567");
        when(identity.currentUser(jwt)).thenReturn(Optional.of(account));
        when(identity.updateCurrentUser(any(), any(), any())).thenReturn(account);
        service.updateProfile(customerId, update("Nimal Perera", "0712 345 678", null), jwt);
        verify(identity).updateCurrentUser(eq(jwt), isNull(), eq("+94712345678"));
    }

    @Test
    void whenIdentityCannotBeReadEverythingIsSentAndAFirstCopyIsCreated() {
        when(profiles.findById(customerId)).thenReturn(Optional.empty());
        when(identity.currentUser(jwt)).thenReturn(Optional.empty());
        when(identity.updateCurrentUser(jwt, "Nimal Perera", "+94771234567")).thenReturn(account);
        var profile = service.updateProfile(customerId, update("Nimal Perera", "0771234567", "data:image/png;base64,AAAA"), jwt);
        assertThat(profile.email()).isEqualTo("nimal@example.com");
        assertThat(profile.avatarBase64()).isEqualTo("data:image/png;base64,AAAA");
        verify(profiles).save(any(CustomerProfile.class));
    }

    @Test
    void blankAvatarRemovesThePhotoAndNullKeepsIt() {
        CustomerProfile copy = stored("Nimal Perera", "nimal@example.com", "+94771234567");
        copy.setAvatarBase64("data:image/png;base64,OLD");
        when(identity.currentUser(jwt)).thenReturn(Optional.of(account));

        service.updateProfile(customerId, update("Nimal Perera", null, null), jwt);
        assertThat(copy.getAvatarBase64()).isEqualTo("data:image/png;base64,OLD");

        service.updateProfile(customerId, update("Nimal Perera", null, ""), jwt);
        assertThat(copy.getAvatarBase64()).isNull();
    }

    @ParameterizedTest
    @CsvSource(value = {"0771234567,+94771234567", "94771234567,+94771234567", "+94771234567,+94771234567",
            "771234567,+94771234567", "'(077) 123-4567',+94771234567", "'   ',NULL", "NULL,NULL"}, nullValues = "NULL")
    void localSriLankanNumbersAreConvertedToE164(String input, String expected) {
        assertThat(IdentityClient.toE164(input)).isEqualTo(expected);
    }
}
