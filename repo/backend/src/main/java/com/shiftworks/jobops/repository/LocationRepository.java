package com.shiftworks.jobops.repository;

import com.shiftworks.jobops.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LocationRepository extends JpaRepository<Location, Long> {

    List<Location> findByActiveTrueOrderByStateAscCityAsc();

    List<Location> findByActiveTrueAndStateOrderByCityAsc(String state);

    @Query("select distinct l.state from Location l where l.active = true order by l.state")
    List<String> findActiveStates();

    boolean existsByStateIgnoreCaseAndCityIgnoreCase(String state, String city);
}
