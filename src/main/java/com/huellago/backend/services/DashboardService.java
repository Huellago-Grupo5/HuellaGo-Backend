package com.huellago.backend.services;

import com.huellago.backend.dtos.DashboardDTO;

public interface DashboardService {
    public DashboardDTO obtenerDashboard(Long usuarioId);
}
