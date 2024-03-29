package me.raducapatina.server.core;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.AccessLevel;
import lombok.Getter;
import me.raducapatina.server.command.ArticleCommand;
import me.raducapatina.server.command.StopCommand;
import me.raducapatina.server.command.UserCommand;
import me.raducapatina.server.command.core.CommandHandler;
import me.raducapatina.server.network.ServerNetworkService;
import me.raducapatina.server.util.HibernateUtil;
import me.raducapatina.server.util.ResourceServerProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class ServerInstance {

    private static final Logger logger = LogManager.getLogger(ServerInstance.class);

    @Getter(AccessLevel.PUBLIC) private final int port;
    @Getter(AccessLevel.PUBLIC) private volatile List<Client> connectedClients;
    private CommandHandler commandHandler;

    public ServerInstance() {
        this.port = Integer.parseInt(ResourceServerProperties.getInstance().getObject("port").toString());
        this.connectedClients = new ArrayList<>(0);
    }

    public void start() {

        logger.info("Starting server on port {0}...".replace("{0}", String.valueOf(port)));

        commandHandler = new CommandHandler()
                .addCommand(new StopCommand(this))
                .addCommand(new UserCommand())
                .addCommand(new ArticleCommand());

        commandHandler.listen();

        try {
            logger.info("Connecting to database...");
            HibernateUtil.getSessionFactory().openSession();
            logger.info("Successfully connected to database.");
        } catch (Exception e) {
            logger.fatal(e.getMessage());
            stop();
        }

        logger.info("Starting Network Service...");
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ServerNetworkService(this))
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            logger.info("Server finished loading.");
            b.bind(port).sync().channel().closeFuture().sync();
        } catch (InterruptedException e) {
            stop();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    public void stop() {
        logger.info("Stopping server..."); // Stopping server...
        commandHandler.stop();
        HibernateUtil.getSessionFactory().close();
        System.exit(0);
    }
}
