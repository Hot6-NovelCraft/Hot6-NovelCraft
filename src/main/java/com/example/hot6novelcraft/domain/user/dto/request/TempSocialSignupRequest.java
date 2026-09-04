package com.example.hot6novelcraft.domain.user.dto.request;

import com.example.hot6novelcraft.domain.user.entity.enums.ProviderSns;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;

import java.time.LocalDate;

public record TempSocialSignupRequest(
        String providerId
        , ProviderSns providerSns
        , String nickname
        , String phoneNo
        ,
        @JsonSerialize(using = LocalDateSerializer.class)
        @JsonDeserialize(using = LocalDateDeserializer.class)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate birthday

) implements java.io.Serializable {
}
