<template>
  <ContentWrap>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="积分：规则驱动的计分器"
      description="规则 = 模块 + 动作 + 次数上限 + 时间窗 + 分值，再加一层扩展加成（缺陷严重程度 s1+3/s2+2/s3+1、任务优先级 p1+2/p2+1、密码强度、执行关闭的项目经理/成员加成）。几条照抄禅道的特例：缺陷「确认」的分给提单人而不是确认人；需求关闭额外给创建者 2 分；执行关闭时项目经理 20 分、成员各 5 分，按期或提前完成再加分。"
    />
  </ContentWrap>

  <ContentWrap>
    <el-row :gutter="15" class="mb-15px">
      <el-col :span="6">
        <el-card shadow="never">
          <div class="text-12px text-gray-400">当前积分</div>
          <div class="text-24px font-bold">{{ total.total }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="text-12px text-gray-400">昨日新增</div>
          <div class="text-24px font-bold">{{ total.yesterday }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="text-12px text-gray-400">流水条数</div>
          <div class="text-24px font-bold">{{ total.count }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="text-12px text-gray-400">功能开关</div>
          <div class="text-24px font-bold">
            <el-tag :type="total.enabled ? 'success' : 'info'">{{ total.enabled ? '已开启' : '已关闭' }}</el-tag>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <el-alert v-if="total.tip" type="success" :closable="false" show-icon :title="total.tip" class="mb-10px" />
    <div class="text-12px text-gray-400">
      总积分 = SUM(zt_score.score)。禅道把它冗余在 zt_user.score 上（还带 scoreLevel 等级），
      本项目没迁 zt_user，所以按流水求和、before/after 在插入时算快照。
    </div>
  </ContentWrap>

  <el-tabs v-model="tab" class="px-10px">
    <el-tab-pane label="积分记录" name="list">
      <ContentWrap>
        <el-form class="-mb-15px" :inline="true" label-width="70px">
          <el-form-item label="账号">
            <el-input v-model="query.account" placeholder="不填=当前登录账号" clearable class="!w-180px" @keyup.enter="load" />
          </el-form-item>
          <el-form-item label="模块">
            <el-input v-model="query.module" placeholder="task / bug / user…" clearable class="!w-160px" @keyup.enter="load" />
          </el-form-item>
          <el-form-item>
            <el-button @click="load"><Icon icon="ep:refresh" class="mr-5px" /> 刷新</el-button>
          </el-form-item>
        </el-form>
        <el-table v-loading="loading" :data="list" empty-text="还没有积分记录">
          <el-table-column label="编号" prop="id" width="80" align="center" />
          <el-table-column label="账号" prop="account" width="110" />
          <el-table-column label="模块" prop="moduleName" width="100" />
          <el-table-column label="动作" prop="methodName" min-width="130" />
          <el-table-column label="描述" prop="desc" min-width="180" show-overflow-tooltip />
          <el-table-column label="之前" prop="before" width="80" align="center" />
          <el-table-column label="分值" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.score >= 0 ? 'success' : 'danger'" size="small">+{{ row.score }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="之后" prop="after" width="80" align="center" />
          <el-table-column label="时间" width="170" align="center">
            <template #default="{ row }">{{ formatDate(row.time) }}</template>
          </el-table-column>
        </el-table>
        <Pagination :total="totalCount" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="load" />
      </ContentWrap>
    </el-tab-pane>

    <el-tab-pane label="积分规则" name="rule">
      <ContentWrap v-loading="ruleLoading">
        <el-table :data="rules" empty-text="没有规则">
          <el-table-column label="模块" prop="moduleName" width="100" />
          <el-table-column label="动作" prop="methodName" width="150" />
          <el-table-column label="次数上限" prop="times" width="100" align="center" />
          <el-table-column label="时间窗（小时）" prop="hour" width="130" align="center" />
          <el-table-column label="分值" prop="score" width="80" align="center" />
          <el-table-column label="扩展加成" prop="desc" min-width="280" show-overflow-tooltip />
          <el-table-column label="code" width="150">
            <template #default="{ row }">
              <span class="font-mono text-12px">{{ row.module }}.{{ row.method }}</span>
            </template>
          </el-table-column>
        </el-table>
        <div class="mt-10px text-12px text-gray-400">
          规则表在代码里（`ScoreRules.java`），与禅道 `module/score/config.php` 一一对应 ——
          禅道里这些分值也是配置不是数据，管理员只能开关积分功能，不能改分数。
          「次数上限 + 时间窗」的实现口径：hour=0 时数全量历史，hour&gt;0 时数**当天**（禅道实现如此）。
        </div>
      </ContentWrap>
    </el-tab-pane>

    <el-tab-pane label="计分试算（联调用）" name="probe">
      <ContentWrap>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="mb-15px"
          title="对应禅道 score::create(module, method, param, account, time)"
          description="禅道里这个动作是被别的模块调的（任务完成、缺陷解决、执行关闭、登录……），本实现开了显式入口，用来联调与验规则。命中次数/时间窗上限会静默跳过；规则不存在会明确报错（禅道内部调用是静默跳过，接口调用报错更好排查）。"
        />
        <el-form :inline="true" label-width="80px">
          <el-form-item label="模块">
            <el-input v-model="probe.module" class="!w-140px" placeholder="task" />
          </el-form-item>
          <el-form-item label="动作">
            <el-input v-model="probe.method" class="!w-160px" placeholder="finish" />
          </el-form-item>
          <el-form-item label="对象编号">
            <el-input v-model="probe.param" class="!w-120px" placeholder="1" />
          </el-form-item>
          <el-form-item label="账号">
            <el-input v-model="probe.account" class="!w-140px" placeholder="不填=当前登录账号" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="runProbe" v-hasPermi="['zentao:score:create']">
              <Icon icon="ep:medal" class="mr-5px" /> 计分
            </el-button>
          </el-form-item>
        </el-form>
        <el-alert v-if="probeResult" :type="probeResult.type" :closable="false" show-icon :title="probeResult.title" />
      </ContentWrap>
    </el-tab-pane>
  </el-tabs>
</template>

<script lang="ts" setup>
import * as ScoreApi from '@/api/zentao/score'
import { formatDate } from '@/utils/formatTime'

defineOptions({ name: 'ZentaoScore' })

const message = useMessage()
const tab = ref('list')
const loading = ref(false)
const ruleLoading = ref(false)
const list = ref<ScoreApi.ScoreVO[]>([])
const totalCount = ref(0)
const query = reactive<any>({ pageNo: 1, pageSize: 10 })
const total = ref<ScoreApi.ScoreTotalVO>({ account: '', total: 0, yesterday: 0, count: 0, enabled: true })
const rules = ref<ScoreApi.ScoreRuleVO[]>([])

const probe = reactive<{ module: string; method: string; param: string; account: string }>({
  module: 'user',
  method: 'login',
  param: '',
  account: ''
})
const probeResult = ref<{ type: 'success' | 'error'; title: string }>()

const load = async () => {
  loading.value = true
  try {
    const data = await ScoreApi.getScorePage(query)
    list.value = data.list
    totalCount.value = data.total
    total.value = await ScoreApi.getScoreTotal(query.account)
  } finally {
    loading.value = false
  }
}

const loadRules = async () => {
  ruleLoading.value = true
  try {
    rules.value = await ScoreApi.getScoreRules()
  } finally {
    ruleLoading.value = false
  }
}

const runProbe = async () => {
  try {
    const data = await ScoreApi.createScore({
      module: probe.module,
      method: probe.method,
      param: probe.param ? Number(probe.param) : undefined,
      account: probe.account || undefined
    })
    if (data && data.id) {
      probeResult.value = {
        type: 'success',
        title: `计分成功：${data.moduleName} / ${data.methodName} +${data.score}（${data.before} → ${data.after}）`
      }
      message.success('已计分')
      await load()
    } else {
      probeResult.value = {
        type: 'success',
        title: '没有产生流水：命中「次数上限 / 时间窗」或该规则分值为 0（禅道的静默跳过语义）'
      }
    }
  } catch (e: any) {
    probeResult.value = { type: 'error', title: `计分失败：${e?.code ?? ''} ${e?.msg ?? e ?? ''}` }
  }
}

onMounted(async () => {
  await Promise.all([load(), loadRules()])
})
</script>
