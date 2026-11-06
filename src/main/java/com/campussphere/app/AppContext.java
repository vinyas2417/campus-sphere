package com.campussphere.app;

import com.campussphere.service.*;
import com.campussphere.repository.JdbcRepository;

public record AppContext(JdbcRepository repository, Session session, AuthService auth,
                         CampusService campus, WorkflowService workflows) { }
