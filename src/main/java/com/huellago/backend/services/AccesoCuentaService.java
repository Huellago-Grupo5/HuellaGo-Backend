package com.huellago.backend.services;

import com.huellago.backend.dtos.LoginDTO;
import com.huellago.backend.dtos.TokenDTO;

public interface AccesoCuentaService {
    TokenDTO login(LoginDTO datos);
}
