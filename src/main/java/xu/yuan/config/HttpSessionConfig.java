package xu.yuan.config;

import org.springframework.stereotype.Component;

import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;

/**
 * http会话配置
 *
 * @author xuyuan
 */
@Component
public class HttpSessionConfig extends ServerEndpointConfig.Configurator implements ServletRequestListener {

    /**
     * 请求初始化
     *
     * @param sre 行为
     */
    @Override
    public void requestInitialized(ServletRequestEvent sre) {
        //获取HttpSession，将所有的请求都获得HttpSession
        HttpSession httpSession = ((HttpServletRequest) sre.getServletRequest()).getSession();
    }

    /**
     * 修改握手
     *
     * @param sec      证券交易委员会
     * @param request  请求
     * @param response 响应
     */
    @Override
    public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
        // 获取session
        HttpSession httpSession = (HttpSession) request.getHttpSession();
        if (httpSession != null) {
            // session放入serverEndpointConfig
            sec.getUserProperties().put(HttpSession.class.getName(), httpSession);
        }
        super.modifyHandshake(sec, request, response);
    }
}
