import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint("/websocket/chat/{roomId}")
public class ChatEndpoint {

    private static final Map<String, Set<Session>> rooms = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("roomId") String roomId) {
        java.util.List<String> names = session.getRequestParameterMap().get("username");
        if (names != null && !names.isEmpty()) {
            session.getUserProperties().put("username", names.get(0));
        }
        java.util.List<String> hostParam = session.getRequestParameterMap().get("host");
        if (hostParam != null && hostParam.contains("1")) {
            session.getUserProperties().put("host", true);
        }
        rooms.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    @OnMessage
    public void onMessage(String message, Session session, @PathParam("roomId") String roomId) {
        for (Session s : rooms.getOrDefault(roomId, Set.of())) {
            if (s.isOpen() && !s.equals(session)) {
                s.getAsyncRemote().sendText(message);
            }
        }
    }

    @OnError
    public void onError(Session session, Throwable t, @PathParam("roomId") String roomId) {
        removeFromRoom(session, roomId);
    }

    @OnClose
    public void onClose(Session session, @PathParam("roomId") String roomId) {
        removeFromRoom(session, roomId);
    }

    private static void removeFromRoom(Session session, String roomId) {
        Set<Session> members = rooms.get(roomId);
        if (members == null || !members.remove(session)) return;

        // Host left: tear down the room and close every guest
        if (Boolean.TRUE.equals(session.getUserProperties().get("host"))) {
            rooms.remove(roomId); // remove first so the guests' own onClose calls do nothing
            for (Session s : members) {
                try {
                    if (s.isOpen()) {
                        s.close(new CloseReason(CloseReason.CloseCodes.GOING_AWAY, "host_left"));
                    }
                } catch (IOException ignored) {}
            }
            return;
        }

        if (members.isEmpty()) {
            rooms.remove(roomId);
            return;
        }
        String name = (String) session.getUserProperties().get("username");
        if (name == null) return;

        String json = "{\"type\":\"leave\",\"username\":\""
                + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
        for (Session s : members) {
            if (s.isOpen()) s.getAsyncRemote().sendText(json);
        }
    }
}