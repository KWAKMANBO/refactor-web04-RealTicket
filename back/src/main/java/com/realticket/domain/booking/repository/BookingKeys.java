package com.realticket.domain.booking.repository;

/**
 * 예매 관련 Redis 키. 모든 키는 이벤트 단위로 나뉘어 있어 마감 시 KEYS/SCAN 없이 지울 수 있다.
 * <pre>
 * open-booking:opened                       Set     오픈된 이벤트 id
 * in-booking:default-max-size               String  좌석 선택 화면 최대 인원 기본값
 * in-booking:{e}:max-size                   String  이벤트별 최대 인원
 *
 * waiting-queue:{e}                         List    대기 중인 sid (앞이 먼저)
 * waiting-queue:{e}:order                   String  마지막으로 발급한 대기 번호
 * waiting-queue:{e}:orders                  Hash    sid -> 대기 번호
 *
 * entering:{e}                              ZSet    입장 허가된 sid (score: 허가 시각 ms)
 * entering:{e}:amounts                      Hash    sid -> 좌석 화면 진입 전에 고른 인원 수
 *
 * in-booking:{e}:sessions                   Set     좌석 선택 중인 sid
 * in-booking:{e}:session:{sid}              Hash    { bookingAmount, saved }
 * in-booking:{e}:session:{sid}:seats        Set     선점한 좌석 "sectionIndex:seatIndex"
 *
 * event:{e}:sections:len                    String  구역 수
 * event:{e}:section:{i}:seats               Bitmap  1: 예약 가능, 0: 예약됨/좌석 없음
 * event:{e}:section:{i}:seats:len           String  구역의 칸 수
 * </pre>
 */
public final class BookingKeys {

    public static final String OPENED_EVENTS = "open-booking:opened";
    public static final String DEFAULT_MAX_SIZE = "in-booking:default-max-size";

    private BookingKeys() {
    }

    public static String maxSize(long eventId) {
        return "in-booking:" + eventId + ":max-size";
    }

    public static String waitingQueue(long eventId) {
        return "waiting-queue:" + eventId;
    }

    public static String waitingOrder(long eventId) {
        return "waiting-queue:" + eventId + ":order";
    }

    public static String waitingOrders(long eventId) {
        return "waiting-queue:" + eventId + ":orders";
    }

    public static String entering(long eventId) {
        return "entering:" + eventId;
    }

    public static String enteringAmounts(long eventId) {
        return "entering:" + eventId + ":amounts";
    }

    public static String inBookingSessions(long eventId) {
        return "in-booking:" + eventId + ":sessions";
    }

    public static String inBookingSession(long eventId, String sid) {
        return "in-booking:" + eventId + ":session:" + sid;
    }

    public static String inBookingSeats(long eventId, String sid) {
        return "in-booking:" + eventId + ":session:" + sid + ":seats";
    }

    public static String sectionsLen(long eventId) {
        return "event:" + eventId + ":sections:len";
    }

    /** Lua 에서 "{prefix}{sectionIndex}:seats" 로 좌석 키를 만들 때 쓰는 접두사 */
    public static String sectionPrefix(long eventId) {
        return "event:" + eventId + ":section:";
    }

    public static String sectionSeats(long eventId, int sectionIndex) {
        return sectionPrefix(eventId) + sectionIndex + ":seats";
    }

    public static String sectionSeatsLen(long eventId, int sectionIndex) {
        return sectionSeats(eventId, sectionIndex) + ":len";
    }

    public static String seatMember(int sectionIndex, int seatIndex) {
        return sectionIndex + ":" + seatIndex;
    }
}
