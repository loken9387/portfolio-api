package com.frausto.web.terminal;

import com.frausto.service.terminal.TerminalSessionAuthorizer;
import com.frausto.model.terminal.dto.TerminalSessionRequest;
import com.frausto.model.terminal.dto.TerminalSessionDescriptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Optional;

@RestController
@RequestMapping("/api/terminal")
public class TerminalController {

    private final TerminalSessionAuthorizer authorizer;

    public TerminalController(TerminalSessionAuthorizer authorizer) {
        this.authorizer = authorizer;
    }

    @PostMapping("/sessions")
    public ResponseEntity<TerminalSessionDescriptor> requestSession(@RequestBody TerminalSessionRequest request) {
        if (request == null || !StringUtils.hasText(request.containerId())) {
            return ResponseEntity.badRequest().build();
        }

        if (!authorizer.isAuthorized(request.containerId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Optional<String> cmd = StringUtils.hasText(request.cmd()) ? Optional.of(request.cmd()) : Optional.empty();

        String websocketPath = UriComponentsBuilder.fromPath("/ws/terminal")
                .queryParam("containerId", request.containerId())
                .queryParamIfPresent("cmd", cmd)
                .build()
                .toUriString();

        TerminalSessionDescriptor descriptor = new TerminalSessionDescriptor(
                request.containerId(),
                request.cmd(),
                websocketPath
        );
        return ResponseEntity.ok(descriptor);
    }
}
