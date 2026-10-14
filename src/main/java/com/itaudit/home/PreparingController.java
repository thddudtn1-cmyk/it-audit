package com.itaudit.home;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.itaudit.auth.Menu;

/**
 * 아직 구현 전인 메뉴의 임시 화면. 각 기능(F-02~F-08)을 구현하면서 해당 매핑을 실제 컨트롤러로 옮긴다.
 */
@Controller
public class PreparingController {

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        return preparing(model, Menu.DASHBOARD);
    }

    @GetMapping("/my-tasks")
    public String myTasks(Model model) {
        return preparing(model, Menu.MY_TASKS);
    }

    @GetMapping("/checklist")
    public String checklist(Model model) {
        return preparing(model, Menu.CHECKLIST);
    }

    @GetMapping("/rounds")
    public String rounds(Model model) {
        return preparing(model, Menu.ROUNDS);
    }

    @GetMapping("/requests")
    public String requests(Model model) {
        return preparing(model, Menu.REQUESTS);
    }

    @GetMapping("/improvements")
    public String improvements(Model model) {
        return preparing(model, Menu.IMPROVEMENTS);
    }

    private String preparing(Model model, Menu menu) {
        model.addAttribute("menu", menu);
        return "common/preparing";
    }
}
