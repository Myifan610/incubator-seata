package org.apache.seata.apm.skywalking.plugin.common;

import io.netty.channel.Channel;
import org.apache.seata.core.protocol.AbstractMessage;
import org.apache.seata.core.protocol.RpcMessage;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

public class SWSeataUtils {

    private static final ILog LOGGER = LogManager.getLogger(SWSeataUtils.class);

    public static String convertPeer(Channel channel) {
        String peer = channel.remoteAddress().toString();
        if (peer.startsWith("/")) {
            peer = peer.substring(1);
        }
        return peer;
    }

    public static String convertOperationName(RpcMessage rpcMessage) {
        if (rpcMessage == null || rpcMessage.getBody() == null) {
            return ComponentsDefine.SEATA.getName();
        }
        String requestSimpleName = rpcMessage.getBody().getClass().getSimpleName();
        if (SeataPluginConfig.Plugin.SEATA.SERVER) {
            return ComponentsDefine.SEATA.getName() + "/TC/" + requestSimpleName;
        }
        if (SWSeataConstants.isTransactionManagerOperationName(requestSimpleName)) {
            return ComponentsDefine.SEATA.getName() + "/TM/" + requestSimpleName;
        }
        return ComponentsDefine.SEATA.getName() + "/RM/" + requestSimpleName;
    }

    public static String convertXid(RpcMessage rpcMessage) {
        if (rpcMessage == null || rpcMessage.getBody() == null) {
            return null;
        }
        if (!(rpcMessage.getBody() instanceof AbstractMessage)) {
            return null;
        }

        AbstractMessage subMessage = (AbstractMessage) rpcMessage.getBody();
        String requestSimpleName = subMessage.getClass().getSimpleName();

        String xid = null;
        try {
            Class<?> clz = SWSeataConstants.TRANSACTION_TRANSMISSION_CLASS_NAME_MAPPING.get(requestSimpleName);
            if (clz != null) {
                xid = (String) clz.getDeclaredMethod("getXid").invoke(subMessage);
            }
        } catch (Throwable e) {
            LOGGER.error("convert seata xid failure", e);
        }
        return xid;
    }
}
