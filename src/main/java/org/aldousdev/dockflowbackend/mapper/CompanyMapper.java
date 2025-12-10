package org.aldousdev.dockflowbackend.mapper;

import org.aldousdev.dockflowbackend.auth.dto.request.CompanyRequest;
import org.aldousdev.dockflowbackend.auth.dto.response.CompanyResponse;
import org.aldousdev.dockflowbackend.auth.entity.Company;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CompanyMapper {

    // Маппинг для ответа
    CompanyResponse toDto(Company company);

    // Маппинг для создания — id и даты игнорируем, JPA их проставит
    Company toEntity(CompanyRequest request);

    // Маппинг для обновления существующей компании
    void updateCompany(CompanyRequest request, @MappingTarget Company company);
}
