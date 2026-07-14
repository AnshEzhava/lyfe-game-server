package com.ansh.lyfegameserver.controller.casino;

import com.ansh.lyfegameserver.dto.casino.CasinoPlayRequest;
import com.ansh.lyfegameserver.dto.casino.CasinoPlayResponse;
import com.ansh.lyfegameserver.service.casino.CasinoService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/casino")
public class CasinoController {

    private final CasinoService casinoService;

    public CasinoController(CasinoService casinoService) {
        this.casinoService = casinoService;
    }

    @PostMapping("/play")
    public ResponseEntity<?> play(@RequestBody CasinoPlayRequest req, JwtAuthenticationToken auth) {
        try {
            return ResponseEntity.ok(casinoService.play(auth.getName(), req));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(
                new CasinoPlayResponse(1, e.getMessage(), req.game(), false, req.bet(), 0, 0, "", List.of(), null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(
                new CasinoPlayResponse(1, "User not found", req.game(), false, req.bet(), 0, 0, "", List.of(), null));
        }
    }
}
