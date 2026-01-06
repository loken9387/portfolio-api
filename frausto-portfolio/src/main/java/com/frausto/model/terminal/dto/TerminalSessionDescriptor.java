package com.frausto.model.terminal.dto;

public record TerminalSessionDescriptor(String containerId, String cmd, String websocketPath) {
}
