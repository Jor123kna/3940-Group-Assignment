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
        // Client vanished mid-close or mid-send: just drop it from the room.
        removeFromRoom(session, roomId);
    }

    @OnClose
    public void onClose(Session session, @PathParam("roomId") String roomId) {
        removeFromRoom(session, roomId);
    }

    private static void removeFromRoom(Session session, String roomId) {
        Set<Session> members = rooms.get(roomId);
        if (members != null) {
            members.remove(session);
            if (members.isEmpty()) rooms.remove(roomId);
        }
    }
}