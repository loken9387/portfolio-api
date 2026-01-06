package com.frausto.web.terminal.dto;

public record TerminalSessionDescriptor(String containerId, String cmd, String websocketPath) {
}
