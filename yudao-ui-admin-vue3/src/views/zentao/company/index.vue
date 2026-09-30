<template>
  <ContentWrap>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="公司信息（禅道里叫「组织视图」）"
      description="禅道 company 模块有三个入口：公司信息（view/edit）、组织成员（browse，按部门看用户）、组织动态（dynamic，全公司动作流）。本页三块都做了，但后两块不重复实现 —— 组织成员用 organization 模块的接口、组织动态用 action 模块的接口。两条禅道口径照抄：① admins 是逗号串（如 ,admin,），是禅道判超管的唯一依据；② getOutsideCompanies() 就是 id != 1，服务外部干系人的所属公司。"
    />
  </ContentWrap>

  <el-tabs v-model="tab" class="px-10px">
    <!-- ① 公司信息 -->
    <el-tab-pane label="公司信息" name="info">
      <ContentWrap>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="编号">{{ company.id }}</el-descriptions-item>
          <el-descriptions-item label="公司名称">{{ company.name }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ company.phone }}</el-descriptions-item>
          <el-descriptions-item label="传真">{{ company.fax }}</el-descriptions-item>
          <el-descriptions-item label="邮政编码">{{ company.zipcode }}</el-descriptions-item>
          <el-descriptions-item label="匿名登录">
            <el-tag :type="company.guest === 1 ? 'warning' : 'info'" size="small">
              {{ company.guest === 1 ? '允许（yudao 侧不生效）' : '不允许' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="官网" :span="2">{{ company.website || '—' }}</el-descriptions-item>
          <el-descriptions-item label="内网">{{ company.backyard || '—' }}</el-descriptions-item>
          <el-descriptions-item label="通讯地址" :span="2">{{ company.address }}</el-descriptions-item>
          <el-descriptions-item label="管理员（admins）">
            <span class="font-mono">{{ company.admins }}</span>
          </el-descriptions-item>
        </el-descriptions>
        <div class="mt-15px">
          <el-button type="primary" @click="openEdit" v-hasPermi="['zentao:company:update']">
            <Icon icon="ep:edit" class="mr-5px" /> 编辑公司信息
          </el-button>
          <span class="ml-10px text-12px text-gray-400">
            禅道没有「删除公司」这个 action（多公司是付费版能力），所以这里也没有删除按钮
          </span>
        </div>
      </ContentWrap>
    </el-tab-pane>

    <!-- ② 外部公司 -->
    <el-tab-pane label="外部公司" name="outside">
      <ContentWrap>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="mb-10px"
          title="id != 1 的公司就是「外部公司」"
          description="禅道 getOutsideCompanies() 的判据就是 id != 1。它服务「外部干系人」：加外部人员时会往 zt_user 建一条 type=outside 的记录，company 指向这里；公司不存在还能顺手新建一条（insert-on-the-fly）。所以「新建公司」在禅道里不是一个独立入口，而是干系人流程里的一步。"
        />
        <div class="mb-10px">
          <el-button type="primary" plain @click="openCreate" v-hasPermi="['zentao:company:create']">
            <Icon icon="ep:plus" class="mr-5px" /> 新建公司
          </el-button>
        </div>
        <el-table v-loading="loading" :data="outsideList" empty-text="还没有外部公司">
          <el-table-column label="下拉文本 text" prop="text" min-width="200" />
          <el-table-column label="值 value" prop="value" width="100" align="center" />
          <el-table-column label="搜索键 keys" prop="keys" min-width="200" />
        </el-table>
        <div class="mt-10px text-12px text-gray-400">
          接口返回的就是上面这三个字段（text/value/keys）—— 这是禅道 ajaxGetOutsideCompany 的原样结构，
          前端下拉不用做二次转换。
        </div>
      </ContentWrap>
    </el-tab-pane>

    <!-- ③ 超管口径对照 -->
    <el-tab-pane label="超管口径对照" name="admins">
      <ContentWrap v-loading="adminsLoading">
        <el-row :gutter="15">
          <el-col :span="8">
            <el-card shadow="never">
              <template #header><span class="font-bold">禅道口径</span></template>
              <div class="text-12px text-gray-400 mb-8px">zt_company.admins（逗号串）</div>
              <el-tag v-for="a in admins.zentaoAdmins" :key="a" class="mr-5px mb-5px">{{ a }}</el-tag>
              <div v-if="!admins.zentaoAdmins?.length" class="text-gray-400">无</div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="never">
              <template #header><span class="font-bold">yudao 口径</span></template>
              <div class="text-12px text-gray-400 mb-8px">拥有 super_admin 角色的用户</div>
              <el-tag v-for="a in admins.yudaoSuperAdmins" :key="a" type="success" class="mr-5px mb-5px">{{ a }}</el-tag>
              <div v-if="!admins.yudaoSuperAdmins?.length" class="text-gray-400">无</div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="never">
              <template #header><span class="font-bold">差异</span></template>
              <div class="mb-8px">
                已对齐：<el-tag v-for="a in admins.matched" :key="a" size="small" class="mr-5px">{{ a }}</el-tag>
                <span v-if="!admins.matched?.length" class="text-gray-400">无</span>
              </div>
              <div class="mb-8px">
                只在禅道侧：<el-tag v-for="a in admins.onlyInZentao" :key="a" size="small" type="warning" class="mr-5px">{{ a }}</el-tag>
                <span v-if="!admins.onlyInZentao?.length" class="text-gray-400">无</span>
              </div>
              <div>
                只在 yudao 侧：<el-tag v-for="a in admins.onlyInYudao" :key="a" size="small" type="danger" class="mr-5px">{{ a }}</el-tag>
                <span v-if="!admins.onlyInYudao?.length" class="text-gray-400">无</span>
              </div>
            </el-card>
          </el-col>
        </el-row>
        <el-alert type="warning" :closable="false" show-icon class="mt-15px" :title="admins.note" />
      </ContentWrap>
    </el-tab-pane>

    <!-- ④ 组织成员：复用 organization 模块 -->
    <el-tab-pane label="组织成员（复用 organization）" name="users">
      <ContentWrap v-loading="userLoading">
        <el-table :data="users" empty-text="没有成员">
          <el-table-column label="账号" prop="account" width="120" />
          <el-table-column label="姓名" prop="realname" width="140" />
          <el-table-column label="部门" prop="deptName" width="160" />
          <el-table-column label="角色" prop="roleNames" min-width="160" />
          <el-table-column label="邮箱" prop="email" min-width="200" />
        </el-table>
      </ContentWrap>
    </el-tab-pane>

    <!-- ⑤ 组织动态：复用 action 模块 -->
    <el-tab-pane label="组织动态（复用 action）" name="dynamic">
      <ContentWrap>
        <el-radio-group v-model="period" class="mb-10px" @change="loadDynamic">
          <el-radio-button label="all">全部</el-radio-button>
          <el-radio-button label="today">今天</el-radio-button>
          <el-radio-button label="yesterday">昨天</el-radio-button>
          <el-radio-button label="thisWeek">本周</el-radio-button>
          <el-radio-button label="thisMonth">本月</el-radio-button>
        </el-radio-group>
        <el-timeline v-loading="dynamicLoading">
          <el-timeline-item
            v-for="a in dynamic"
            :key="a.id"
            :timestamp="formatDate(a.date)"
            placement="top"
          >
            <span class="font-bold">{{ a.actor }}</span>
            <span class="ml-5px">{{ a.renderedDesc || a.actionName }}</span>
          </el-timeline-item>
        </el-timeline>
        <div v-if="!dynamic.length" class="text-gray-400">这个周期没有动态</div>
        <div class="mt-10px text-12px text-gray-400">
          禅道的组织动态有「上周/上月」，本项目的 action 模块目前只支持 全部/今天/昨天/本周/本月（记在 README 的「已知限制」里）
        </div>
      </ContentWrap>
    </el-tab-pane>
  </el-tabs>

  <el-dialog v-model="formVisible" :title="formTitle" width="620px" append-to-body>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="公司名称" prop="name">
        <el-input v-model="form.name" placeholder="必填，且不能与已有公司重名" />
      </el-form-item>
      <el-form-item label="联系电话">
        <el-input v-model="form.phone" class="!w-260px" />
      </el-form-item>
      <el-form-item label="传真">
        <el-input v-model="form.fax" class="!w-260px" />
      </el-form-item>
      <el-form-item label="通讯地址">
        <el-input v-model="form.address" />
      </el-form-item>
      <el-form-item label="邮政编码">
        <el-input v-model="form.zipcode" class="!w-200px" />
      </el-form-item>
      <el-form-item label="官网">
        <el-input v-model="form.website" placeholder="只填 http:// 会被清空（禅道行为）" />
      </el-form-item>
      <el-form-item label="内网">
        <el-input v-model="form.backyard" placeholder="只填 http:// 会被清空（禅道行为）" />
      </el-form-item>
      <el-form-item label="匿名登录">
        <el-switch v-model="form.guest" :active-value="1" :inactive-value="0" />
        <span class="ml-8px text-12px text-gray-400">字段照存；yudao 的认证统一走 OAuth2，没有匿名登录</span>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="formVisible = false">取 消</el-button>
      <el-button type="primary" :loading="saving" @click="submitForm">确 定</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import * as CompanyApi from '@/api/zentao/company'
import { getUserList } from '@/api/zentao/organization'
import { getActionDynamic } from '@/api/zentao/action'
import { formatDate } from '@/utils/formatTime'
import type { ActionTimelineVO } from '@/api/zentao/action'

defineOptions({ name: 'ZentaoCompany' })

const message = useMessage()
const tab = ref('info')
const loading = ref(false)
const saving = ref(false)

const company = ref<CompanyApi.CompanyVO>({ name: '' })
const outsideList = ref<CompanyApi.CompanyOptionVO[]>([])
const admins = ref<CompanyApi.CompanyAdminsVO>({
  zentaoAdmins: [],
  yudaoSuperAdmins: [],
  matched: [],
  onlyInZentao: [],
  onlyInYudao: [],
  note: ''
})
const adminsLoading = ref(false)
const users = ref<any[]>([])
const userLoading = ref(false)
const dynamic = ref<ActionTimelineVO[]>([])
const dynamicLoading = ref(false)
const period = ref('all')

const formVisible = ref(false)
const formTitle = ref('')
const formRef = ref()
const form = ref<CompanyApi.CompanyVO>({ name: '' })
const rules = { name: [{ required: true, message: '公司名称不能为空', trigger: 'blur' }] }

const loadCompany = async () => {
  company.value = await CompanyApi.getFirstCompany()
}

const loadOutside = async () => {
  loading.value = true
  try {
    outsideList.value = await CompanyApi.getOutsideList()
  } finally {
    loading.value = false
  }
}

const loadAdmins = async () => {
  adminsLoading.value = true
  try {
    admins.value = await CompanyApi.getCompanyAdmins()
  } finally {
    adminsLoading.value = false
  }
}

const loadUsers = async () => {
  userLoading.value = true
  try {
    users.value = await getUserList({})
  } finally {
    userLoading.value = false
  }
}

const loadDynamic = async () => {
  dynamicLoading.value = true
  try {
    dynamic.value = await getActionDynamic({ period: period.value, limit: 20 })
  } finally {
    dynamicLoading.value = false
  }
}

const openEdit = () => {
  form.value = { ...company.value }
  formTitle.value = '编辑公司信息'
  formVisible.value = true
}

const openCreate = () => {
  form.value = { name: '', guest: 0, website: 'http://', backyard: 'http://' }
  formTitle.value = '新建公司'
  formVisible.value = true
}

const submitForm = async () => {
  // Element Plus 的 validate() 校验失败时是 **reject**（带字段错误对象），
  // 直接 await 而不 catch，在「点确定但必填没填」时就会冒一条未捕获的 unhandledrejection ——
  // 页面看起来正常（表单上照样飘红），但浏览器检查会判成 JS 报错（坑位 #53）。
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    if (form.value.id) {
      await CompanyApi.updateCompany(form.value)
      message.success('已保存')
    } else {
      await CompanyApi.createCompany(form.value)
      message.success('已创建')
    }
    formVisible.value = false
    await Promise.all([loadCompany(), loadOutside(), loadAdmins()])
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await loadCompany()
  await Promise.all([loadOutside(), loadAdmins(), loadUsers(), loadDynamic()])
})
</script>
