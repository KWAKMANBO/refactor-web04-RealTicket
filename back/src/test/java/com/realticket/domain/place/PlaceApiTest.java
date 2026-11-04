package com.realticket.domain.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.realticket.domain.place.entity.Place;
import com.realticket.domain.place.entity.Section;
import com.realticket.domain.place.repository.PlaceRepository;
import com.realticket.domain.place.repository.SectionRepository;
import com.realticket.support.IntegrationTest;

class PlaceApiTest extends IntegrationTest {

    @Autowired
    PlaceRepository placeRepository;

    @Autowired
    SectionRepository sectionRepository;

    @Nested
    @DisplayName("좌석 정보 조회")
    class GetSeats {

        @Test
        void 구역_순서대로_좌석_정보를_반환한다() throws Exception {
            Place place = fixtures.place("대극장", "A", "B");

            mockMvc.perform(get("/place/seat/{id}", place.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(place.getId()))
                    .andExpect(jsonPath("$.layout.overview").value("/overview.svg"))
                    .andExpect(jsonPath("$.layout.overviewWidth").value(1500))
                    .andExpect(jsonPath("$.layout.overviewHeight").value(1000))
                    .andExpect(jsonPath("$.layout.overviewPoints").value("[]"))
                    .andExpect(jsonPath("$.layout.sections.length()").value(2))
                    .andExpect(jsonPath("$.layout.sections[0].name").value("A"))
                    .andExpect(jsonPath("$.layout.sections[0].colLen").value(2))
                    .andExpect(jsonPath("$.layout.sections[0].seats.length()").value(4))
                    .andExpect(jsonPath("$.layout.sections[0].seats[0]").value(true))
                    .andExpect(jsonPath("$.layout.sections[0].seats[3]").value(false))
                    .andExpect(jsonPath("$.layout.sections[1].name").value("B"));
        }

        @Test
        void 없는_장소면_400() throws Exception {
            mockMvc.perform(get("/place/seat/{id}", 999))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("해당 장소가 존재하지 않습니다."));
        }
    }

    @Nested
    @DisplayName("장소 추가")
    class CreatePlace {

        Map<String, Object> request = Map.of(
                "name", "대극장",
                "address", "서울특별시",
                "overviewSvg", "/overview.svg",
                "overviewHeight", 1000,
                "overviewWidth", 1500,
                "overviewPoints", "[{\"id\":\"1\",\"points\":[[0,0],[1,1]]}]");

        @Test
        void 관리자는_장소를_추가할_수_있다() throws Exception {
            mockMvc.perform(post("/place").cookie(adminCookie())
                            .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                    .andExpect(status().isCreated());

            assertThat(placeRepository.findAll()).singleElement()
                    .satisfies(place -> {
                        assertThat(place.getName()).isEqualTo("대극장");
                        assertThat(place.getSectionIds()).isEmpty();
                    });
        }

        @Test
        void 관리자가_아니면_401() throws Exception {
            mockMvc.perform(post("/place").cookie(loginCookie())
                            .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void 로그인하지_않았으면_403() throws Exception {
            mockMvc.perform(post("/place")
                            .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void overviewPoints가_JSON이_아니면_400() throws Exception {
            Map<String, Object> invalid = new java.util.HashMap<>(request);
            invalid.put("overviewPoints", "{not json");

            mockMvc.perform(post("/place").cookie(adminCookie())
                            .contentType(MediaType.APPLICATION_JSON).content(json(invalid)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void 필수값이_없으면_400과_메시지_배열() throws Exception {
            mockMvc.perform(post("/place").cookie(adminCookie())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"대극장\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").isArray());
        }
    }

    @Nested
    @DisplayName("구역 추가")
    class CreateSections {

        @Test
        void order_순서대로_구역_순서가_정해진다() throws Exception {
            Place place = fixtures.place("대극장");
            List<Map<String, Object>> request = List.of(
                    section("두번째", place.getId(), 2),
                    section("첫번째", place.getId(), 1));

            mockMvc.perform(post("/place/section").cookie(adminCookie())
                            .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                    .andExpect(status().isCreated());

            List<Long> sectionIds = placeRepository.findById(place.getId()).orElseThrow().getSectionIds();
            List<String> names = sectionIds.stream()
                    .map(id -> sectionRepository.findById(id).orElseThrow())
                    .map(Section::getName)
                    .toList();
            assertThat(names).containsExactly("첫번째", "두번째");
        }

        @Test
        void 서로_다른_장소의_구역이_섞이면_400() throws Exception {
            Place first = fixtures.place("A극장");
            Place second = fixtures.place("B극장");

            mockMvc.perform(post("/place/section").cookie(adminCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(List.of(section("a", first.getId(), 1), section("b", second.getId(), 2)))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("섹션은 모두 동일한 place에 삽입해야 합니다."));
        }

        @Test
        void 없는_장소면_404() throws Exception {
            mockMvc.perform(post("/place/section").cookie(adminCookie())
                            .contentType(MediaType.APPLICATION_JSON).content(json(List.of(section("a", 999L, 1)))))
                    .andExpect(status().isNotFound());
        }

        @Test
        void 빈_목록이면_400() throws Exception {
            mockMvc.perform(post("/place/section").cookie(adminCookie())
                            .contentType(MediaType.APPLICATION_JSON).content("[]"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void 항목_검증에_실패하면_400() throws Exception {
            Place place = fixtures.place("대극장");
            Map<String, Object> invalid = Map.of("name", "a", "placeId", place.getId(), "order", 1);

            mockMvc.perform(post("/place/section").cookie(adminCookie())
                            .contentType(MediaType.APPLICATION_JSON).content(json(List.of(invalid))))
                    .andExpect(status().isBadRequest());
        }

        private Map<String, Object> section(String name, Long placeId, int order) {
            return Map.of("name", name, "colLen", 2, "seats", List.of(true, false), "placeId", placeId,
                    "order", order);
        }
    }

    @Nested
    @DisplayName("장소 삭제")
    class DeletePlace {

        @Test
        void 장소와_구역을_함께_삭제한다() throws Exception {
            Place place = fixtures.place("대극장", "A", "B");

            mockMvc.perform(delete("/place/{id}", place.getId()).cookie(adminCookie()))
                    .andExpect(status().isOk());

            assertThat(placeRepository.count()).isZero();
            assertThat(sectionRepository.count()).isZero();
        }

        @Test
        void 프로그램이_사용_중인_장소는_409() throws Exception {
            Place place = fixtures.place("대극장", "A");
            fixtures.program("맘마미아", place);

            mockMvc.perform(delete("/place/{id}", place.getId()).cookie(adminCookie()))
                    .andExpect(status().isConflict());

            assertThat(placeRepository.count()).isEqualTo(1);
            assertThat(sectionRepository.count()).isEqualTo(1);
        }

        @Test
        void 없는_장소면_404() throws Exception {
            mockMvc.perform(delete("/place/{id}", 999).cookie(adminCookie()))
                    .andExpect(status().isNotFound());
        }
    }
}
