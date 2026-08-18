/** interface 是接口   所有审查 Agent 都必须实现两个方法 */
package com.codeguard.agent.agent;

import com.codeguard.agent.domain.AgentType;
import com.codeguard.agent.domain.ReviewContext;

public interface ReviewAgent {

    /** 返回自己是哪种 Agent */
    AgentType type();

    /** 执行审查 */
    AgentExecutionResult review(ReviewContext context);
}