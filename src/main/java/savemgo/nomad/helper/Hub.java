package savemgo.nomad.helper;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.query.Query;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import savemgo.nomad.db.DB;
import savemgo.nomad.entity.Lobby;
import savemgo.nomad.entity.News;
import savemgo.nomad.packet.Packet;
import savemgo.nomad.util.Packets;
import savemgo.nomad.util.Util;

public class Hub {

	private static final Logger logger = LogManager.getLogger(Hub.class);

	public static void getGameLobbyInfo(ChannelHandlerContext ctx) {
		AtomicReference<ByteBuf[]> payloads = new AtomicReference<>();
		Session session = null;
		try {
			session = DB.getSession();
			session.beginTransaction();

			Query<Lobby> query = session.createQuery("from Lobby", Lobby.class);
			List<Lobby> lobbies = query.list();

			session.getTransaction().commit();
			DB.closeSession(session);

			Packets.handleMutliElementPayload(ctx, lobbies.size(), 8, 0x23, payloads, (i, bo) -> {
				Lobby lobby = lobbies.get(i);

				int unk1 = 0;

				int attributes = (lobby.getSubtype() << 24) | unk1;
				int openTime = 0, closeTime = 0, isOpen = 1;

				bo.writeInt(i).writeInt(attributes).writeShort(lobby.getId());
				Util.writeString(lobby.getName(), 16, bo);
				bo.writeInt(openTime).writeInt(closeTime).writeByte(isOpen);
			});

			Packets.write(ctx, 0x4901, 0);
			Packets.write(ctx, 0x4902, payloads);
			Packets.write(ctx, 0x4903, 0);
		} catch (Exception e) {
			logger.error("Exception while getting game lobby info.", e);
			DB.rollbackAndClose(session);
			Util.releaseBuffers(payloads);
			Packets.writeError(ctx, 0x4901, 1);
		}
	}

	public static void getLobbyList(ChannelHandlerContext ctx) {
		AtomicReference<ByteBuf[]> payloads = new AtomicReference<>();
		Session session = null;
		try {
			session = DB.getSession();
			session.beginTransaction();

			Query<Lobby> query = session.createQuery("from Lobby", Lobby.class);
			List<Lobby> lobbies = query.list();

			session.getTransaction().commit();
			DB.closeSession(session);

			Packets.handleMutliElementPayload(ctx, lobbies.size(), 22, 0x2e, payloads, (i, bo) -> {
				Lobby lobby = lobbies.get(i);

				int beginner = 0, expansion = 0, noHeadshot = 0;

				int restriction = 0;
				if (beginner == 1) {
					restriction += 2;
				}
				if (expansion == 1) {
					restriction += 8;
				}
				if (noHeadshot == 1) {
					restriction += 16;
				}

				bo.writeInt(i).writeInt(lobby.getType());
				Util.writeString(lobby.getName(), 16, bo);
				Util.writeString(lobby.getIp(), 15, bo);
				bo.writeShort(lobby.getPort()).writeShort(lobby.getPlayers()).writeShort(lobby.getId())
						.writeByte(restriction);
			});

			Packets.write(ctx, 0x2002, 0);
			Packets.write(ctx, 0x2003, payloads);
			Packets.write(ctx, 0x2004, 0);
		} catch (Exception e) {
			logger.error("Exception while getting lobby list.", e);
			DB.rollbackAndClose(session);
			Util.releaseBuffers(payloads);
			Packets.writeError(ctx, 0x2002, 1);
		}
	}

	public static void getNews(ChannelHandlerContext ctx) {
		// In: 01
		ByteBuf[] bos = null;
		Session session = null;
		try {
			session = DB.getSession();
			session.beginTransaction();

			Query<News> query = session.createQuery("from News", News.class);
			List<News> news = query.list();

			session.getTransaction().commit();
			DB.closeSession(session);

			int newsItems = news.size();

			bos = new ByteBuf[newsItems];

			for (int i = 0; i < newsItems; i++) {
				News newsItem = news.get(i);

				String message = newsItem.getMessage();

				int length = Math.min(message.length(), Packet.MAX_PAYLOAD_LENGTH - 138);
				message = message.substring(0, length);

				bos[i] = ctx.alloc().directBuffer(138 + length);
				ByteBuf bo = bos[i];

				bo.writeInt(newsItem.getId()).writeBoolean(newsItem.getImportant()).writeInt(newsItem.getTime());
				Util.writeString(newsItem.getTopic(), 128, bo);
				Util.writeString(message, length + 1, bo);
			}

			Packets.write(ctx, 0x2009, 0);
			Packets.write(ctx, 0x200a, bos);
			Packets.write(ctx, 0x200b, 0);
		} catch (Exception e) {
			logger.error("Exception while getting news.", e);
			DB.rollbackAndClose(session);
			Util.releaseBuffers(bos);
			Packets.writeError(ctx, 0x2009, 1);
		}
	}

	public static void getGameEntryInfo(ChannelHandlerContext ctx) {
		ByteBuf bo = null;
		try {
			bo = ctx.alloc().directBuffer(0xac);

			bo.writeInt(0).writeInt(1).writeZero(0xa4);

			Packets.write(ctx, 0x4991, bo);
		} catch (Exception e) {
			logger.error("Exception while getting game entry info.", e);
			Packets.writeError(ctx, 0x4991, 1);
			Util.releaseBuffer(bo);
		}
	}

	public static void onLobbyDisconnect(ChannelHandlerContext ctx, Packet in) {
		// In: 00
		Packets.write(ctx, 0x4151);
	}

	private static final byte[] TRAINING_BYTES = new byte[] { (byte) 0x00, (byte) 0x0A, (byte) 0x00, (byte) 0x15,
			(byte) 0x00, (byte) 0x3A, (byte) 0x00, (byte) 0x08, (byte) 0x00, (byte) 0x61 };

	public static void onTrainingConnect(ChannelHandlerContext ctx, Packet in) {
		// In: 08
		ByteBuf bo = Unpooled.wrappedBuffer(TRAINING_BYTES);
		Packets.write(ctx, 0x43d1, bo);
	}

}
