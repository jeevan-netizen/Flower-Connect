package com.flowerconnect.geo.repository;

import com.flowerconnect.geo.domain.ServiceLocationProposal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ServiceLocationProposalRepository extends JpaRepository<ServiceLocationProposal, Long> {

    Optional<ServiceLocationProposal> findByCityAndAreaAndStatus(String city, String area,
                                                                 ServiceLocationProposal.Status status);
}
