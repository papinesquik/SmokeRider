import * as admin from "firebase-admin";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { setGlobalOptions, logger } from "firebase-functions/v2";

admin.initializeApp();
setGlobalOptions({ region: "europe-west1" });

export const notifyRidersOnPendingOrder = onDocumentWritten(
  "orders/{orderId}",
  async (event) => {
    const after = event.data?.after?.data() as any | undefined;
    const before = event.data?.before?.data() as any | undefined;

    const becamePending =
      after && after.status === "pending" && (!before || before.status !== "pending");
    if (!becamePending) return;

    const orderId = event.params.orderId as string;
    const clientId = (after.clientId ?? "").toString().trim();
    if (!clientId) {
      logger.warn("Order pending ma clientId mancante", { orderId });
      return;
    }

    const db = admin.firestore();

    // city del cliente (positions per uid)
    const clientPosQ = await db.collection("positions")
      .where("uid", "==", clientId).limit(1).get();
    const clientCityRaw = (clientPosQ.docs[0]?.get("city") ?? "").toString().trim();
    if (!clientCityRaw) {
      logger.info("City cliente non trovata in positions", { orderId, clientId });
      return;
    }
    const clientCityLc = clientCityRaw.toLowerCase();
    logger.info("Order became pending", { orderId, clientId, clientCity: clientCityRaw });

    // rider online & attivi
    const ridersSnap = await db.collection("users")
      .where("role", "==", "rider")
      .where("active", "==", true)
      .where("online", "==", true)
      .get();
    logger.info("Rider candidati (online & attivi)", { count: ridersSnap.size });

    // token -> uid (dedup)
    const tokenToUid = new Map<string, string>();
    await Promise.all(
      ridersSnap.docs.map(async (uDoc) => {
        const uid = uDoc.id;
        const token = (uDoc.get("fcmToken") ?? "").toString().trim();
        if (!token) return;

        const rPosQ = await db.collection("positions")
          .where("uid", "==", uid).limit(1).get();
        const rCityRaw = (rPosQ.docs[0]?.get("city") ?? "").toString().trim();
        if (!rCityRaw) return;

        if (rCityRaw.toLowerCase() === clientCityLc) tokenToUid.set(token, uid);
      })
    );

    const tokens = Array.from(tokenToUid.keys());
    logger.info("Rider nella stessa città del cliente", { tokensCount: tokens.length });
    if (!tokens.length) return;

    // DATA-ONLY (l’app decide se mostrare la notifica)
    const resp = await admin.messaging().sendEachForMulticast({
      tokens,
      data: {
        kind: "order_pending",
        orderId,
        clientCity: clientCityRaw,
        title: "Nuovo ordine nella tua città",
        body: "Tocca per vedere i dettagli",
      },
      android: { priority: "high" },
    });

    // 🔎 Logga dettagli degli errori (senza toccare i token salvati)
    resp.responses.forEach((r, i) => {
      if (!r.success && r.error) {
        logger.info("FCM failure", {
          index: i,
          code: r.error.code,
          message: r.error.message,
          tokenSuffix: tokens[i]?.slice(-8), // solo ultime 8 per debug
        });
      }
    });

    logger.info("Push inviata", {
      orderId,
      notified: resp.successCount,
      failures: resp.failureCount,
    });
  }
);
