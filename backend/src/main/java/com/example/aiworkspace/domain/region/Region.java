package com.example.aiworkspace.domain.region;

import com.example.aiworkspace.domain.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "regions",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_regions_sido_sigungu", columnNames = {"sido_code", "sigungu_code"})
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Region extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sido_code", nullable = false, length = 20)
    private String sidoCode;

    @Column(name = "sido_name", nullable = false, length = 100)
    private String sidoName;

    @Column(name = "sigungu_code", nullable = false, length = 20)
    private String sigunguCode;

    @Column(name = "sigungu_name", nullable = false, length = 100)
    private String sigunguName;

    @Column(name = "kma_nx")
    private Integer kmaNx;

    @Column(name = "kma_ny")
    private Integer kmaNy;

    @Column(name = "asos_station_id", length = 20)
    private String asosStationId;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;
}
