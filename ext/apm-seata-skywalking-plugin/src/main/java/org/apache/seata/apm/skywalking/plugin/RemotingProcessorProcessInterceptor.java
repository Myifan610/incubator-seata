package org.apache.seata.apm.skywalking.plugin;

import com.alipay.sofa.common.profile.StringUtil;
import org.apache.seata.apm.skywalking.plugin.common.SWSeataUtils;
import org.apache.seata.core.protocol.AbstractMessage;
import org.apache.seata.core.protocol.RpcMessage;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.StringTag;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import java.lang.reflect.Method;

public class RemotingProcessorProcessInterceptor implements InstanceMethodsAroundInterceptor {

    private RpcMessage findRpcMessage(Object[] allArguments) {
        if (allArguments == null) {
            return null;
        }
        for (Object arg : allArguments) {
            if (arg instanceof RpcMessage) {
                return (RpcMessage) arg;
            }
        }
        return null;
    }

    @Override
    public void beforeMethod(
            EnhancedInstance objInst,
            Method method,
            Object[] allArguments,
            Class<?>[] argumentsTypes,
            MethodInterceptResult result)
            throws Throwable {

        RpcMessage rpcMessage = findRpcMessage(allArguments);
        if (rpcMessage == null) {
            return;
        }

        String operationName = SWSeataUtils.convertOperationName(rpcMessage);
        ContextCarrier contextCarrier = new ContextCarrier();
        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            next.setHeadValue(rpcMessage.getHeadMap().get(next.getHeadKey()));
        }
        AbstractSpan activeSpan = ContextManager.createEntrySpan(operationName, contextCarrier);
        SpanLayer.asRPCFramework(activeSpan);
        activeSpan.setComponent(ComponentsDefine.SEATA);

        String xid = SWSeataUtils.convertXid(rpcMessage);
        if (StringUtil.isNotBlank(xid)) {
            activeSpan.tag(new StringTag(20, "Seata.xid"), xid);
        }
    }

    @Override
    public Object afterMethod(
            EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Object ret)
            throws Throwable {

        RpcMessage rpcMessage = findRpcMessage(allArguments);
        if (rpcMessage != null && rpcMessage.getBody() instanceof AbstractMessage) {
            ContextManager.stopSpan();
        }
        return ret;
    }

    @Override
    public void handleMethodException(
            EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Throwable t) {}
}
