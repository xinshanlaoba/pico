package com.picojava.cli;

import com.picojava.agent.Pico;

import java.util.Scanner;

public class ReplRunner {
    private final Pico pico;

    public ReplRunner(Pico pico) {
        this.pico = pico;
    }

    public int run() throws Exception {
        System.out.println("输入 /help 查看可用命令。");
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("pico> ");
            if (!scanner.hasNextLine()) return 0;
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;
            switch (line) {
                case "/help" -> {
                    System.out.println("/help    显示帮助");
                    System.out.println("/memory  显示分层记忆");
                    System.out.println("/session 显示 session id");
                    System.out.println("/reset   清空当前 session 上下文");
                    System.out.println("/exit    退出 agent");
                }
                case "/memory" -> System.out.println(pico.memoryText());
                case "/session" -> System.out.println(pico.session().getId());
                case "/reset" -> {
                    pico.clearContext();
                    pico.saveSession();
                    System.out.println("session 已清空");
                }
                case "/exit", "/quit" -> { return 0; }
                default -> {
                    String answer = pico.ask(line);
                    System.out.println(answer);
                }
            }
        }
    }
}
