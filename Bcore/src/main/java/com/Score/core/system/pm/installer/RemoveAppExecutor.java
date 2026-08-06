package com.Score.core.system.pm.installer;

import com.Score.core.env.BEnvironment;
import com.Score.core.system.pm.BPackageSettings;
import com.Score.entity.pm.InstallOption;
import com.Score.utils.FileUtils;

/**
 * Created by Milk on 4/27/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class RemoveAppExecutor implements Executor {
    @Override
    public int exec(BPackageSettings ps, InstallOption option, int userId) {
        FileUtils.deleteDir(BEnvironment.getAppDir(ps.pkg.packageName));
        return 0;
    }
}
